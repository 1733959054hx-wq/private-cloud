package front.workspace.personalworkspace.service;

import com.alibaba.fastjson.JSON;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.entity.FileAccessLog;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.documentspace.repository.FileAccessLogRepository;
import front.workspace.personalworkspace.dto.DashboardVO;
import front.workspace.personalworkspace.repository.DocFavoriteRepository;
import front.intelligence.notification.repository.SysNotificationRepository;
import front.system.entity.SysUser;
import front.system.repository.SysOperationLogRepository;
import front.system.repository.SysUserRepository;
import front.workflow.entity.ApprovalRequest;
import front.workflow.repository.ApprovalRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class DashboardService {

    private static final String DASHBOARD_PREFIX = "dashboard:data:";
    private static final String STORAGE_PREFIX = "dashboard:storage:";
    private static final long BASE_TTL_MINUTES = 3;
    private static final int TTL_JITTER_SECONDS = 60;
    private static final Random RANDOM = new Random();

    @Autowired
    private DocFavoriteRepository favoriteRepository;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private SysNotificationRepository notificationRepository;

    @Autowired
    private FileAccessLogRepository accessLogRepository;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private SysOperationLogRepository operationLogRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public DashboardVO getDashboardData(Long userId) {
        try {
            String key = DASHBOARD_PREFIX + userId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JSON.parseObject(JSON.toJSONString(cached), DashboardVO.class);
            }
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取工作台缓存失败: " + e.getMessage());
        }

        DashboardVO dashboard = buildDashboardData(userId);

        try {
            String key = DASHBOARD_PREFIX + userId;
            long ttl = BASE_TTL_MINUTES + RANDOM.nextInt(TTL_JITTER_SECONDS) / 60;
            redisTemplate.opsForValue().set(key, dashboard, ttl, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 写入工作台缓存失败: " + e.getMessage());
        }
        return dashboard;
    }

    public void evictDashboard(Long userId) {
        try {
            redisTemplate.delete(DASHBOARD_PREFIX + userId);
            redisTemplate.delete(STORAGE_PREFIX + userId);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 清除工作台缓存失败: " + e.getMessage());
        }
    }

    private DashboardVO buildDashboardData(Long userId) {
        DashboardVO dashboard = new DashboardVO();
        dashboard.setFavoriteCount(favoriteRepository.countByUserId(userId));
        dashboard.setTotalDocs(fileRepository.countByDeletedAndStatus(0, 1));
        dashboard.setMonthlyUploads(fileRepository.countMonthlyUploads(userId,
                LocalDateTime.now().minusMonths(1)));

        // 真实待审批数据
        List<ApprovalRequest> pendingList = approvalRequestRepository
                .findByApplicantIdOrderByCreateTimeDesc(userId).stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 0)
                .collect(java.util.stream.Collectors.toList());
        dashboard.setPendingApprovals(pendingList.size());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<DashboardVO.PendingApproval> approvalVOs = pendingList.stream().map(a -> {
            String title = a.getTitle();
            if (title == null || title.isEmpty()) {
                title = "审批请求 #" + a.getId();
            }
            return new DashboardVO.PendingApproval(
                    a.getId(),
                    title,
                    a.getType() != null ? a.getType() : "document",
                    a.getStatus() == 0 ? "PENDING" : "APPROVED",
                    a.getCreateTime() != null ? a.getCreateTime().format(fmt) : ""
            );
        }).collect(java.util.stream.Collectors.toList());
        dashboard.setPendingApprovalList(approvalVOs);

        dashboard.setUnreadNotifications(notificationRepository.countByUserIdAndIsRead(userId, 0));
        dashboard.setRecentFiles(getRecentFiles(userId));
        dashboard.setStorageStats(buildStorageStats(userId));
        dashboard.setContributionHeatmap(buildContributionHeatmap(userId));
        return dashboard;
    }

    private DashboardVO.StorageStats buildStorageStats(Long userId) {
        try {
            String key = STORAGE_PREFIX + userId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JSON.parseObject(JSON.toJSONString(cached), DashboardVO.StorageStats.class);
            }
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取存储缓存失败: " + e.getMessage());
        }

        DashboardVO.StorageStats stats = new DashboardVO.StorageStats();

        SysUser user = sysUserRepository.findById(userId).orElse(null);
        long quota = (user != null && user.getStorageQuota() != null) ? user.getStorageQuota() : 10737418240L;
        stats.setTotalQuota(quota);

        Long totalUsed = fileRepository.sumFileSizeByUser(userId);
        stats.setTotalUsed(totalUsed != null ? totalUsed : 0L);

        List<DashboardVO.StorageByType> byType = new ArrayList<>();
        try {
            List<Map<String, Object>> typeAgg = fileRepository.sumFileSizeByTypeForUser(userId);
            java.util.Map<String, Long> merged = new java.util.LinkedHashMap<>();
            for (Map<String, Object> row : typeAgg) {
                Object ftObj = row.get("fileType");
                if (ftObj == null) ftObj = row.get("filetype");
                if (ftObj == null) ftObj = row.get("FILETYPE");
                String fileType = ftObj != null ? ftObj.toString() : "other";
                Object sizeObj = row.get("totalSize");
                if (sizeObj == null) sizeObj = row.get("totalsize");
                if (sizeObj == null) sizeObj = row.get("TOTALSIZE");
                long size = 0L;
                if (sizeObj instanceof Number) {
                    size = ((Number) sizeObj).longValue();
                }
                String normalized = normalizeFileType(fileType);
                merged.merge(normalized, size, Long::sum);
            }
            for (java.util.Map.Entry<String, Long> entry : merged.entrySet()) {
                byType.add(new DashboardVO.StorageByType(entry.getKey(), entry.getValue()));
            }
        } catch (Exception e) {
            System.err.println("[存储统计] 按类型聚合失败: " + e.getMessage());
        }
        stats.setByType(byType);

        try {
            redisTemplate.opsForValue().set(STORAGE_PREFIX + userId, stats, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 写入存储缓存失败: " + e.getMessage());
        }

        return stats;
    }

    private String normalizeFileType(String fileType) {
        if (fileType == null) return "other";
        String lower = fileType.toLowerCase();
        if (lower.matches("doc|docx|txt|md|pdf|rtf|odt|wps")) return "document";
        if (lower.matches("xls|xlsx|csv|ods")) return "spreadsheet";
        if (lower.matches("ppt|pptx|odp")) return "presentation";
        if (lower.matches("jpg|jpeg|png|gif|bmp|svg|webp|ico")) return "image";
        if (lower.matches("mp4|avi|mkv|mov|wmv|flv|webm")) return "video";
        if (lower.matches("mp3|wav|flac|aac|ogg|wma")) return "audio";
        if (lower.matches("zip|rar|7z|tar|gz|bz2")) return "archive";
        return "other";
    }

    private List<DashboardVO.ContributionDay> buildContributionHeatmap(Long userId) {
        List<DashboardVO.ContributionDay> result = new ArrayList<>();
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(7);
            List<Object[]> rows = operationLogRepository.countByDateForUser(userId, since);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (Object[] row : rows) {
                String date = "";
                if (row[0] instanceof java.sql.Date) {
                    date = ((java.sql.Date) row[0]).toLocalDate().format(fmt);
                } else if (row[0] instanceof LocalDate) {
                    date = ((LocalDate) row[0]).format(fmt);
                } else if (row[0] != null) {
                    date = row[0].toString();
                }
                int count = 0;
                if (row[1] instanceof Number) {
                    count = ((Number) row[1]).intValue();
                }
                if (!date.isEmpty()) {
                    result.add(new DashboardVO.ContributionDay(date, count));
                }
            }
        } catch (Exception e) {
            System.err.println("[热力图] 查询贡献数据失败: " + e.getMessage());
        }
        return result;
    }

    private List<DashboardVO.RecentFile> getRecentFiles(Long userId) {
        List<FileAccessLog> logs = accessLogRepository.findDistinctRecentByUserId(userId, 10);
        List<DashboardVO.RecentFile> recentFiles = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (FileAccessLog log : logs) {
            try {
                DocFile file = fileRepository.findById(log.getFileId()).orElse(null);
                if (file != null && file.getDeleted() == 0 && file.getStatus() == 1) {
                    DashboardVO.RecentFile rf = new DashboardVO.RecentFile();
                    rf.setFileId(file.getId());
                    rf.setFileName(file.getFileName());
                    rf.setFileType(file.getFileType());
                    rf.setFileSize(file.getFileSize());
                    rf.setAccessTime(log.getCreateTime() != null ? log.getCreateTime().format(formatter) : "");
                    recentFiles.add(rf);
                }
            } catch (Exception e) {
                // skip inaccessible files
            }
        }
        return recentFiles;
    }

    /**
     * 获取所在部门空间的最新动态/通知
     */
    public List<Map<String, Object>> getTeamUpdates(Long userId) {
        List<Map<String, Object>> updates = new ArrayList<>();
        try {
            SysUser user = sysUserRepository.findById(userId).orElse(null);
            if (user == null || user.getDepartmentId() == null) return updates;

            Long deptId = user.getDepartmentId();
            // 获取部门内最近的文件上传动态
            List<DocFile> recentDeptFiles = fileRepository.findByDepartmentIdAndDeletedAndStatusOrderByCreateTimeDesc(
                    deptId, 0, 1, org.springframework.data.domain.PageRequest.of(0, 5));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (DocFile f : recentDeptFiles) {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("type", "file_upload");
                item.put("title", f.getFileName());
                item.put("operator", f.getUploaderName());
                item.put("time", f.getCreateTime() != null ? f.getCreateTime().format(fmt) : "");
                item.put("fileId", f.getId());
                updates.add(item);
            }

            // 获取部门内最近的通知
            List<front.intelligence.notification.entity.SysNotification> deptNotifications =
                    notificationRepository.findByUserIdOrderByCreateTimeDesc(userId,
                            org.springframework.data.domain.PageRequest.of(0, 5));
            for (front.intelligence.notification.entity.SysNotification n : deptNotifications) {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("type", "notification");
                item.put("title", n.getTitle());
                item.put("operator", n.getFromUsername());
                item.put("time", n.getCreateTime() != null ? n.getCreateTime().format(fmt) : "");
                item.put("notificationId", n.getId());
                updates.add(item);
            }

            // 按时间倒序排列
            updates.sort((a, b) -> {
                String ta = (String) a.getOrDefault("time", "");
                String tb = (String) b.getOrDefault("time", "");
                return tb.compareTo(ta);
            });

            // 最多返回10条
            if (updates.size() > 10) {
                updates = updates.subList(0, 10);
            }
        } catch (Exception e) {
            System.err.println("[团队动态] 查询失败: " + e.getMessage());
        }
        return updates;
    }
}
