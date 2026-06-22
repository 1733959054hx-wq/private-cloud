package back.admin.dashboard.controller;

import front.hxconfig.Result;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import front.system.repository.SysUserRepository;
import front.workflow.entity.ApprovalRequest;
import front.workflow.repository.ApprovalRequestRepository;
import front.intelligence.search.repository.SearchKeywordRepository;
import front.intelligence.search.entity.SearchKeyword;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理后台仪表盘 API
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private DocFileRepository docFileRepository;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired(required = false)
    private ApprovalRequestRepository approvalRepository;

    @Autowired(required = false)
    private SearchKeywordRepository searchKeywordRepository;

    /** 概览统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDocs", docFileRepository.countByDeletedAndStatus(0, 1));
        stats.put("totalUsers", sysUserRepository.count());
        stats.put("totalStorage", 10737418240L);
        Long used = docFileRepository.sumFileSize();
        stats.put("usedStorage", used != null ? used : 0L);
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        stats.put("todayUploads", docFileRepository.countUploadsSince(today));
        stats.put("todayDownloads", 0);
        stats.put("monthlyUploads", docFileRepository.countUploadsSince(today.minusMonths(1)));
        stats.put("approvalPassRate", getApprovalPassRate());
        return Result.success(stats);
    }

    /** 文档类型分布 */
    @GetMapping("/doc-types")
    public Result<List<Map<String, Object>>> getDocTypes() {
        List<DocFile> files = docFileRepository.findByDeletedAndStatus(0, 1);
        Map<String, Long> typeCount = files.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getFileType() != null ? f.getFileType().toUpperCase() : "其他",
                        Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : typeCount.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.getKey());
            m.put("count", e.getValue());
            result.add(m);
        }
        return Result.success(result);
    }

    /** 存储趋势（按月统计） */
    @GetMapping("/storage-trend")
    public Result<List<Map<String, Object>>> getStorageTrend() {
        List<Map<String, Object>> result = new ArrayList<>();
        java.time.LocalDate now = java.time.LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            java.time.LocalDate month = now.minusMonths(i);
            String label = month.getMonthValue() + "月";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", label);
            Long size = docFileRepository.sumSizeByMonth(month.getYear(), month.getMonthValue());
            m.put("size", size != null ? size : 0L);
            result.add(m);
        }
        return Result.success(result);
    }

    /** 热门文档 TOP10 */
    @GetMapping("/hot-docs")
    public Result<List<Map<String, Object>>> getHotDocs() {
        List<DocFile> top = docFileRepository.findTopDocsByViewCount(PageRequest.of(0, 10));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            DocFile f = top.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("docId", f.getId());
            m.put("fileName", f.getFileName());
            m.put("viewCount", f.getViewCount() != null ? f.getViewCount() : 0);
            result.add(m);
        }
        return Result.success(result);
    }

    /** 搜索热词 TOP10 */
    @GetMapping("/hot-keywords")
    public Result<List<Map<String, Object>>> getHotKeywords() {
        if (searchKeywordRepository == null) return Result.success(List.of());
        List<SearchKeyword> top = searchKeywordRepository.findByDeletedOrderBySearchCountDesc(0);
        List<Map<String, Object>> result = top.stream().map(k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("keyword", k.getKeyword());
            m.put("count", k.getSearchCount());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /** 待审批列表 */
    @GetMapping("/pending-approvals")
    public Result<List<Map<String, Object>>> getPendingApprovals() {
        if (approvalRepository == null) return Result.success(List.of());
        List<ApprovalRequest> pending = approvalRepository.findByStatusOrderByCreateTimeDesc(0);
        List<Map<String, Object>> result = pending.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("applicant", a.getApplicantId());
            m.put("createTime", a.getCreateTime());
            docFileRepository.findById(a.getDocumentId()).ifPresent(f -> {
                m.put("fileName", f.getFileName());
            });
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    private int getApprovalPassRate() {
        if (approvalRepository == null) return 0;
        try {
            long total = approvalRepository.count();
            long passed = approvalRepository.countByStatus(2);
            return total > 0 ? (int) (passed * 100 / total) : 0;
        } catch (Exception e) { return 0; }
    }
}
