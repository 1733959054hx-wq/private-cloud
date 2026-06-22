package front.workspace.documentspace.service;

import front.search.engine.es.DocFileIndexService;
import front.system.entity.SysOperationLog;
import front.system.repository.SysOperationLogRepository;
import front.workspace.documentspace.dto.FileDTO;
import front.workspace.documentspace.entity.DocDirectory;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.entity.DocFileVersion;
import front.workspace.documentspace.repository.DocDirectoryRepository;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.documentspace.repository.DocFileVersionRepository;
import front.system.entity.SysUser;
import front.system.repository.SysUserRepository;
import front.system.repository.SysUserRoleRepository;
import front.system.repository.SysRoleRepository;
import front.system.entity.SysRole;
import front.intelligence.ocr.repository.OcrRecordRepository;
import front.intelligence.ai.entity.DocTag;
import front.intelligence.ai.repository.DocMetadataRepository;
import front.intelligence.ai.repository.DocTagRepository;
import front.intelligence.comment.repository.DocCommentRepository;
import front.intelligence.comment.entity.DocComment;
import front.intelligence.notification.repository.SysNotificationRepository;
import front.workspace.personalworkspace.repository.DocFavoriteRepository;
import front.storage.service.StorageRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocFileService {

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private DocFileVersionRepository fileVersionRepository;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private SysUserRoleRepository sysUserRoleRepository;

    @Autowired
    private SysRoleRepository sysRoleRepository;

    @Autowired
    private SysOperationLogRepository operationLogRepository;

    @Autowired
    private FileListCacheService fileListCacheService;

    @Autowired(required = false)
    private DocFileIndexService docFileIndexService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private StorageRouter storageRouter;

    private static final String FILE_VIEW_COUNT_KEY = "file:view:count:";
    private static final String FILE_DOWNLOAD_COUNT_KEY = "file:download:count:";
    private static final long COUNT_SYNC_THRESHOLD = 100;

    private String resolveFileNameConflict(String fileName, Long directoryId, Integer spaceType, Long spaceId, Long excludeFileId) {
        boolean nameExists;
        if (spaceType != null && spaceId != null) {
            if (directoryId != null) {
                nameExists = fileRepository.existsByFileNameAndDirectoryIdAndSpaceTypeAndSpaceIdAndDeleted(fileName, directoryId, spaceType, spaceId, 0);
            } else {
                nameExists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndSpaceTypeAndSpaceIdAndDeleted(fileName, spaceType, spaceId, 0);
            }
        } else {
            if (directoryId != null) {
                nameExists = fileRepository.existsByFileNameAndDirectoryIdAndDeleted(fileName, directoryId, 0);
            } else {
                nameExists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndDeleted(fileName, 0);
            }
        }
        if (!nameExists) {
            return fileName;
        }

        String baseName = fileName;
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = fileName.substring(0, dotIdx);
            ext = fileName.substring(dotIdx);
        }
        int counter = 1;
        while (true) {
            String newName = baseName + " (" + counter + ")" + ext;
            if (spaceType != null && spaceId != null) {
                if (directoryId != null) {
                    nameExists = fileRepository.existsByFileNameAndDirectoryIdAndSpaceTypeAndSpaceIdAndDeleted(newName, directoryId, spaceType, spaceId, 0);
                } else {
                    nameExists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndSpaceTypeAndSpaceIdAndDeleted(newName, spaceType, spaceId, 0);
                }
            } else {
                if (directoryId != null) {
                    nameExists = fileRepository.existsByFileNameAndDirectoryIdAndDeleted(newName, directoryId, 0);
                } else {
                    nameExists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndDeleted(newName, 0);
                }
            }
            if (!nameExists) {
                return newName;
            }
            counter++;
        }
    }

    public List<FileDTO> getFilesByDirectory(Long directoryId, Long userId) {
        List<FileDTO> cachedFiles = fileListCacheService.getDirFiles(directoryId);
        if (cachedFiles != null) {
            return attachFavoritesFromDTO(cachedFiles, userId);
        }

        List<DocFile> files;
        if (directoryId != null) {
            files = fileRepository.findByDirectoryIdAndDeletedAndStatusOrderByCreateTimeDesc(directoryId, 0, 1);
        } else {
            files = fileRepository.findByDirectoryIdIsNullAndDeletedAndStatusOrderByCreateTimeDesc(0, 1);
        }
        List<FileDTO> result = files.stream().map(this::toDTO).collect(Collectors.toList());
        fileListCacheService.putDirFiles(directoryId, result);
        return attachFavoritesFromDTO(result, userId);
    }

    public Page<FileDTO> getFilesByDirectoryPage(Long directoryId, Long userId, Pageable pageable) {
        Page<DocFile> page;
        if (directoryId != null) {
            page = fileRepository.findByDirectoryIdAndDeletedAndStatusOrderByCreateTimeDesc(directoryId, 0, 1, pageable);
        } else {
            page = fileRepository.findByDirectoryIdIsNullAndDeletedAndStatusOrderByCreateTimeDesc(0, 1, pageable);
        }
        List<FileDTO> dtos = attachFavorites(page.getContent(), userId);
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public List<FileDTO> getFilesByDirectoryAndDepartment(Long directoryId, Long departmentId, Long userId) {
        List<FileDTO> cachedFiles = fileListCacheService.getDeptDirFiles(departmentId, directoryId);
        if (cachedFiles != null) {
            return attachFavoritesFromDTO(cachedFiles, userId);
        }

        List<DocFile> files;
        if (directoryId != null) {
            files = fileRepository.findByDirectoryIdAndDepartmentAccessible(directoryId, departmentId);
        } else {
            files = fileRepository.findByRootAndDepartmentAccessible(departmentId);
        }
        List<FileDTO> result = files.stream().map(this::toDTO).collect(Collectors.toList());
        fileListCacheService.putDeptDirFiles(departmentId, directoryId, result);
        return attachFavoritesFromDTO(result, userId);
    }

    public Page<FileDTO> getFilesByDirectoryAndDepartmentPage(Long directoryId, Long departmentId, Long userId, Pageable pageable) {
        List<DocFile> files;
        if (directoryId != null) {
            files = fileRepository.findByDirectoryIdAndDepartmentAccessible(directoryId, departmentId);
        } else {
            files = fileRepository.findByRootAndDepartmentAccessible(departmentId);
        }
        // 部门场景暂无原生分页，这里做内存分页兜底（数据量可控）
        return pageInMemory(files, userId, pageable);
    }

    public List<FileDTO> getFilesBySpace(Integer spaceType, Long spaceId, Long directoryId, Long userId) {
        List<FileDTO> cachedFiles = fileListCacheService.getSpaceDirFiles(spaceType, spaceId, directoryId);
        if (cachedFiles != null) {
            return attachFavoritesFromDTO(cachedFiles, userId);
        }

        List<DocFile> files;
        if (directoryId != null) {
            files = fileRepository.findBySpaceAndDirectory(spaceType, spaceId, directoryId);
        } else {
            files = fileRepository.findBySpaceAndRootDirectory(spaceType, spaceId);
        }
        List<FileDTO> result = files.stream().map(this::toDTO).collect(Collectors.toList());
        fileListCacheService.putSpaceDirFiles(spaceType, spaceId, directoryId, result);
        return attachFavoritesFromDTO(result, userId);
    }

    public Page<FileDTO> getFilesBySpacePage(Integer spaceType, Long spaceId, Long directoryId, Long userId, Pageable pageable) {
        List<DocFile> files;
        if (directoryId != null) {
            files = fileRepository.findBySpaceAndDirectory(spaceType, spaceId, directoryId);
        } else {
            files = fileRepository.findBySpaceAndRootDirectory(spaceType, spaceId);
        }
        // 空间场景暂无原生分页，做内存分页兜底
        return pageInMemory(files, userId, pageable);
    }

    private Page<FileDTO> pageInMemory(List<DocFile> files, Long userId, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), files.size());
        List<FileDTO> dtos;
        if (start < files.size()) {
            dtos = attachFavorites(files.subList(start, end), userId);
        } else {
            dtos = new ArrayList<>();
        }
        return new PageImpl<>(dtos, pageable, files.size());
    }

    private List<FileDTO> attachFavorites(List<DocFile> files, Long userId) {
        if (userId == null || files.isEmpty()) {
            return files.stream().map(this::toDTO).collect(Collectors.toList());
        }
        List<Long> fileIds = files.stream().map(DocFile::getId).collect(Collectors.toList());
        Set<Long> favoriteIds = new HashSet<>(
                docFavoriteRepository.findFavoriteTargetIdsByUserAndTargets(userId, fileIds, 0));
        return files.stream().map(file -> {
            FileDTO dto = toDTO(file);
            dto.setIsFavorited(favoriteIds.contains(file.getId()));
            return dto;
        }).collect(Collectors.toList());
    }

    private List<FileDTO> attachFavoritesFromDTO(List<FileDTO> files, Long userId) {
        if (userId == null || files.isEmpty()) {
            return files;
        }
        List<Long> fileIds = files.stream().map(FileDTO::getId).collect(Collectors.toList());
        Set<Long> favoriteIds = new HashSet<>(
                docFavoriteRepository.findFavoriteTargetIdsByUserAndTargets(userId, fileIds, 0));
        files.forEach(dto -> dto.setIsFavorited(favoriteIds.contains(dto.getId())));
        return files;
    }

    /**
     * 获取与指定文件相关的推荐文件列表
     * 推荐策略：相同标签(+3) > 相同目录(+2) > 相同上传者(+1)
     */
    public List<FileDTO> getRelatedFiles(Long fileId, Long userId) {
        DocFile current = fileRepository.findById(fileId).orElse(null);
        if (current == null || current.getDeleted() != 0 || current.getStatus() != 1) {
            return List.of();
        }

        List<DocTag> tags = docTagRepository.findByFileId(fileId);
        Set<String> tagNames = tags.stream()
                .map(DocTag::getTagName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Integer> scoreMap = new HashMap<>();

        // 1. 相同标签 +3 分
        for (String tagName : tagNames) {
            List<Long> fileIds = docTagRepository.findFileIdsByTagName(tagName);
            for (Long id : fileIds) {
                if (!id.equals(fileId)) {
                    scoreMap.merge(id, 3, Integer::sum);
                }
            }
        }

        // 2. 相同目录 +2 分
        if (current.getDirectoryId() != null) {
            List<DocFile> sameDirFiles = fileRepository
                    .findByDirectoryIdAndDeletedAndStatusOrderByCreateTimeDesc(
                            current.getDirectoryId(), 0, 1);
            for (DocFile f : sameDirFiles) {
                if (!f.getId().equals(fileId)) {
                    scoreMap.merge(f.getId(), 2, Integer::sum);
                }
            }
        }

        // 3. 相同上传者 +1 分
        if (current.getUploaderId() != null) {
            List<DocFile> sameUploaderFiles = fileRepository
                    .findByUploaderIdAndDeletedAndStatusOrderByCreateTimeDesc(
                            current.getUploaderId(), 0, 1);
            for (DocFile f : sameUploaderFiles) {
                if (!f.getId().equals(fileId)) {
                    scoreMap.merge(f.getId(), 1, Integer::sum);
                }
            }
        }

        if (scoreMap.isEmpty()) {
            return List.of();
        }

        // 按得分倒序取前 5
        List<Long> topIds = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<DocFile> relatedFiles = fileRepository.findAllById(topIds);
        Map<Long, DocFile> fileMap = relatedFiles.stream()
                .filter(f -> f.getDeleted() == 0 && f.getStatus() == 1)
                .collect(Collectors.toMap(DocFile::getId, f -> f));

        List<FileDTO> result = new ArrayList<>();
        for (Long id : topIds) {
            DocFile f = fileMap.get(id);
            if (f != null && canAccessFile(f, userId)) {
                result.add(toDTO(f));
            }
        }

        // 批量填充推荐文件所在目录名，便于前端展示"文件在哪"
        Set<Long> dirIds = result.stream()
                .map(FileDTO::getDirectoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!dirIds.isEmpty()) {
            Map<Long, String> dirNameMap = docDirectoryRepository.findAllById(dirIds).stream()
                    .collect(Collectors.toMap(DocDirectory::getId, DocDirectory::getDirName));
            for (FileDTO dto : result) {
                if (dto.getDirectoryId() != null) {
                    dto.setDirectoryName(dirNameMap.getOrDefault(dto.getDirectoryId(), null));
                }
            }
        }

        return attachFavoritesFromDTO(result, userId);
    }

    public FileDTO getFileById(Long id) {
        DocFile file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        return toDTO(file);
    }

    /**
     * 增加浏览次数（使用 Redis 原子计数，避免数据库并发更新丢失）
     * 加 @Transactional：当 Redis 计数达到阈值触发 syncViewCountToDb 时，
     * 或 Redis 不可用降级到 fileRepository.incrementViewCount 时，
     * 这些 @Modifying 查询都需要活跃事务。
     */
    @Transactional
    public void incrementViewCount(Long fileId) {
        try {
            if (stringRedisTemplate != null) {
                Long count = stringRedisTemplate.opsForValue().increment(FILE_VIEW_COUNT_KEY + fileId, 1);
                // 达到阈值立即回写数据库
                if (count != null && count >= COUNT_SYNC_THRESHOLD) {
                    syncViewCountToDb(fileId);
                }
                return;
            }
        } catch (Exception e) {
            System.err.println("[Redis] 浏览计数失败: " + e.getMessage());
        }
        // 降级：直接数据库更新
        fileRepository.incrementViewCount(fileId);
    }

    /**
     * 增加下载次数
     * 加 @Transactional：同 incrementViewCount，@Modifying 查询需要活跃事务。
     */
    @Transactional
    public void incrementDownloadCount(Long fileId) {
        try {
            if (stringRedisTemplate != null) {
                Long count = stringRedisTemplate.opsForValue().increment(FILE_DOWNLOAD_COUNT_KEY + fileId, 1);
                if (count != null && count >= COUNT_SYNC_THRESHOLD) {
                    syncDownloadCountToDb(fileId);
                }
                return;
            }
        } catch (Exception e) {
            System.err.println("[Redis] 下载计数失败: " + e.getMessage());
        }
        fileRepository.incrementDownloadCount(fileId);
    }

    private void syncViewCountToDb(Long fileId) {
        String key = FILE_VIEW_COUNT_KEY + fileId;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) return;
        try {
            long increment = Long.parseLong(value);
            if (increment > 0) {
                fileRepository.incrementViewCountBy(fileId, increment);
                stringRedisTemplate.opsForValue().set(key, "0");
            }
        } catch (NumberFormatException ignored) {}
    }

    private void syncDownloadCountToDb(Long fileId) {
        String key = FILE_DOWNLOAD_COUNT_KEY + fileId;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) return;
        try {
            long increment = Long.parseLong(value);
            if (increment > 0) {
                fileRepository.incrementDownloadCountBy(fileId, increment);
                stringRedisTemplate.opsForValue().set(key, "0");
            }
        } catch (NumberFormatException ignored) {}
    }

    /**
     * 定时回写 Redis 计数到数据库（每5分钟）
     * 必须开启事务：fileRepository.incrementViewCountBy / incrementDownloadCountBy
     * 是 @Modifying 查询，Hibernate 要求执行 update/delete 时必须存在活跃事务，
     * 否则抛出 "No active transaction for update or delete query"。
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void syncAllCountsToDb() {
        if (stringRedisTemplate == null) return;
        Set<String> viewKeys = stringRedisTemplate.keys(FILE_VIEW_COUNT_KEY + "*");
        if (viewKeys != null) {
            for (String key : viewKeys) {
                String fileId = key.substring(FILE_VIEW_COUNT_KEY.length());
                syncViewCountToDb(Long.valueOf(fileId));
            }
        }
        Set<String> downloadKeys = stringRedisTemplate.keys(FILE_DOWNLOAD_COUNT_KEY + "*");
        if (downloadKeys != null) {
            for (String key : downloadKeys) {
                String fileId = key.substring(FILE_DOWNLOAD_COUNT_KEY.length());
                syncDownloadCountToDb(Long.valueOf(fileId));
            }
        }
    }

    /**
     * 校验用户是否有权限访问/预览/下载指定文件
     */
    public boolean canAccessFile(Long fileId, Long userId) {
        Optional<DocFile> optional = fileRepository.findById(fileId);
        if (optional.isEmpty()) return false;
        return canAccessFile(optional.get(), userId);
    }

    private boolean canAccessFile(DocFile file, Long userId) {
        // 已删除或状态异常的文件不可访问
        if (file.getDeleted() == null || file.getDeleted() != 0) return false;
        if (file.getStatus() == null || file.getStatus() != 1) return false;

        // 上传者本人始终可访问
        if (userId != null && userId.equals(file.getUploaderId())) return true;

        SysUser user = userId != null ? sysUserRepository.findById(userId).orElse(null) : null;
        List<String> roles = userId != null ? getRoleCodesForUser(userId) : new ArrayList<>();
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        if (isAdmin) return true;

        // 部门文件：用户需属于同部门
        if (file.getDepartmentId() != null) {
            if (user != null && file.getDepartmentId().equals(user.getDepartmentId())) {
                return true;
            }
        }

        // 空间文件：公共空间允许访问；个人空间仅本人（上面已判断）
        if (file.getSpaceType() != null) {
            // 公共空间/企业空间默认允许已登录用户访问
            if (file.getSpaceType() == 2) return true; // enterprise
            if (file.getSpaceType() == 1) { // department
                if (user != null && file.getSpaceId() != null && file.getSpaceId().equals(user.getDepartmentId())) {
                    return true;
                }
            }
            if (file.getSpaceType() == 0) { // personal
                if (userId != null && file.getSpaceId() != null && file.getSpaceId().equals(userId)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Transactional
    public FileDTO updateFile(Long id, FileDTO dto) {
        DocFile file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        Long oldDirectoryId = file.getDirectoryId();
        if (dto.getFileName() != null && !dto.getFileName().equals(file.getFileName())) {
            boolean exists;
            if (file.getDirectoryId() != null) {
                exists = fileRepository.existsByFileNameAndDirectoryIdAndDeleted(dto.getFileName(), file.getDirectoryId(), 0);
            } else {
                exists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndDeleted(dto.getFileName(), 0);
            }
            if (exists) {
                throw new RuntimeException("同一目录下已存在同名文件");
            }
            file.setFileName(dto.getFileName());
        }
        if (dto.getDirectoryId() != null) file.setDirectoryId(dto.getDirectoryId());
        if (dto.getDepartmentId() != null) file.setDepartmentId(dto.getDepartmentId());
        DocFile saved = fileRepository.save(file);
        fileListCacheService.evictDirectory(oldDirectoryId);
        if (dto.getDirectoryId() != null && !dto.getDirectoryId().equals(oldDirectoryId)) {
            fileListCacheService.evictDirectory(dto.getDirectoryId());
        }
        return toDTO(saved);
    }

    @Autowired
    private DocFavoriteRepository docFavoriteRepository;

    @Autowired
    private DocCommentRepository docCommentRepository;

    @Autowired
    private SysNotificationRepository sysNotificationRepository;

    @Autowired
    private OcrRecordRepository ocrRecordRepository;

    @Autowired
    private DocMetadataRepository docMetadataRepository;

    @Autowired
    private DocTagRepository docTagRepository;

    @Autowired
    private DocDirectoryRepository docDirectoryRepository;

    @Transactional
    public void resetFileSmartData(Long fileId) {
        ocrRecordRepository.deleteByFileId(fileId);
        docMetadataRepository.deleteByFileId(fileId);
        docTagRepository.deleteByFileId(fileId);
    }

    @Transactional
    public void softDeleteFile(Long id) {
        DocFile file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        file.setStatus(0);
        fileRepository.save(file);
        resetFileSmartData(id);
        fileListCacheService.evictDirectory(file.getDirectoryId());
        fileListCacheService.evictSpace(file.getSpaceType(), file.getSpaceId(), file.getDirectoryId());
    }

    @Transactional
    public void restoreFile(Long id) {
        DocFile file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        file.setStatus(1);
        fileRepository.save(file);
        fileListCacheService.evictDirectory(file.getDirectoryId());
        fileListCacheService.evictSpace(file.getSpaceType(), file.getSpaceId(), file.getDirectoryId());
    }

    @Transactional
    public void hardDeleteFile(Long id) {
        DocFile file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        // 删除物理文件（本地或 MinIO）
        if (file.getFilePath() != null) {
            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            storageRouter.delete(storageType, file.getFilePath());
            // 若存在预览 PDF 缓存，一并删除
            if (file.getPreviewPdfPath() != null) {
                storageRouter.delete(storageType, file.getPreviewPdfPath());
            }
        }
        file.setDeleted(1);
        // 级联清理：清空全文内容字段，避免删除后仍可通过全文检索访问到已删除文件的文本内容
        file.setFulltextContent(null);
        fileRepository.save(file);
        resetFileSmartData(id);
        fileListCacheService.evictDirectory(file.getDirectoryId());
        fileListCacheService.evictSpace(file.getSpaceType(), file.getSpaceId(), file.getDirectoryId());
    }

    public List<FileDTO> getRecycleBinFiles() {
        List<DocFile> files = fileRepository.findByDeletedAndStatus(0, 0);
        return files.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<FileDTO> searchFiles(String keyword) {
        List<DocFile> files = fileRepository.searchByKeyword(keyword);
        return files.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<FileDTO> fulltextSearchFiles(String keyword) {
        List<DocFile> files = fileRepository.fulltextSearch(keyword);
        return files.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public FileDTO renameFile(Long id, String newName) {
        DocFile file = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        if (newName == null || newName.trim().isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }
        if (newName.equals(file.getFileName())) {
            return toDTO(file);
        }

        String oldExt = getFileExtension(file.getFileName());
        String newExt = getFileExtension(newName);
        if (!oldExt.equalsIgnoreCase(newExt)) {
            throw new RuntimeException("不允许修改文件后缀名，请保持原后缀 \"" + oldExt + "\"");
        }

        boolean exists;
        if (file.getSpaceType() != null && file.getSpaceId() != null) {
            if (file.getDirectoryId() != null) {
                exists = fileRepository.existsByFileNameAndDirectoryIdAndSpaceTypeAndSpaceIdAndDeleted(
                        newName, file.getDirectoryId(), file.getSpaceType(), file.getSpaceId(), 0);
            } else {
                exists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndSpaceTypeAndSpaceIdAndDeleted(
                        newName, file.getSpaceType(), file.getSpaceId(), 0);
            }
        } else {
            if (file.getDirectoryId() != null) {
                exists = fileRepository.existsByFileNameAndDirectoryIdAndDeleted(newName, file.getDirectoryId(), 0);
            } else {
                exists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndDeleted(newName, 0);
            }
        }
        if (exists) {
            throw new RuntimeException("同一目录下已存在同名文件 \"" + newName + "\"，请使用其他名称");
        }

        file.setFileName(newName);
        DocFile saved = fileRepository.save(file);

        fileListCacheService.evictDirectory(file.getDirectoryId());
        fileListCacheService.evictSpace(file.getSpaceType(), file.getSpaceId(), file.getDirectoryId());

        if (docFileIndexService != null) {
            try {
                docFileIndexService.syncDocument(saved);
            } catch (Exception e) {
                System.err.println("[重命名] ES索引同步失败, fileId=" + id + ", error=" + e.getMessage());
            }
        }

        return toDTO(saved);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < fileName.length() - 1) {
            return fileName.substring(dotIdx + 1);
        }
        return "";
    }

    @Transactional
    public FileDTO transferFile(Long fileId, Long targetDirectoryId, Long targetDepartmentId, Long userId) {
        DocFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        Long oldDirectoryId = file.getDirectoryId();

        boolean isCrossDepartment = targetDepartmentId != null
                && file.getDepartmentId() != null
                && !file.getDepartmentId().equals(targetDepartmentId);

        if (isCrossDepartment) {
            boolean hasPermission = checkCrossDepartmentPermission(userId);
            if (!hasPermission) {
                throw new RuntimeException("无跨部门转移权限，需要部门管理员或管理员角色");
            }
            SysUser operator = sysUserRepository.findById(userId).orElse(null);
            if (operator != null && operator.getDepartmentId() != null) {
                List<String> roleCodes = getRoleCodesForUser(userId);
                if (!roleCodes.contains("ROLE_ADMIN") && !operator.getDepartmentId().equals(file.getDepartmentId())) {
                    throw new RuntimeException("部门管理员只能转移本部门的文件");
                }
            }
        }

        if (targetDirectoryId != null) {
            file.setDirectoryId(targetDirectoryId);
        }
        if (targetDepartmentId != null) {
            file.setDepartmentId(targetDepartmentId);
        }
        DocFile saved = fileRepository.save(file);

        fileListCacheService.evictDirectory(oldDirectoryId);
        if (targetDirectoryId != null && !targetDirectoryId.equals(oldDirectoryId)) {
            fileListCacheService.evictDirectory(targetDirectoryId);
        }
        fileListCacheService.evictSpace(file.getSpaceType(), file.getSpaceId(), oldDirectoryId);
        fileListCacheService.evictSpace(file.getSpaceType(), file.getSpaceId(), targetDirectoryId);

        if (isCrossDepartment) {
            logFileTransfer(userId, fileId, file.getDepartmentId(), targetDepartmentId);
        }

        return toDTO(saved);
    }

    @Transactional
    public List<FileDTO> moveFiles(List<Long> fileIds, Long targetDirectoryId, Integer targetSpaceType, Long targetSpaceId, Long userId) {
        List<FileDTO> results = new ArrayList<>();
        SysUser operator = userId != null ? sysUserRepository.findById(userId).orElse(null) : null;

        for (Long fileId : fileIds) {
            DocFile file = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件不存在, id=" + fileId));

            Long oldDirectoryId = file.getDirectoryId();
            Integer oldSpaceType = file.getSpaceType();
            Long oldSpaceId = file.getSpaceId();

            Integer finalSpaceType = targetSpaceType != null ? targetSpaceType : file.getSpaceType();
            Long finalSpaceId = targetSpaceId != null ? targetSpaceId : file.getSpaceId();

            String resolvedFileName = resolveFileNameConflict(file.getFileName(), targetDirectoryId, finalSpaceType, finalSpaceId, fileId);
            if (!resolvedFileName.equals(file.getFileName())) {
                file.setFileName(resolvedFileName);
            }

            boolean spaceChanged = targetSpaceType != null && !targetSpaceType.equals(oldSpaceType);

            if (targetSpaceType != null) file.setSpaceType(targetSpaceType);
            if (targetSpaceId != null) file.setSpaceId(targetSpaceId);
            file.setDirectoryId(targetDirectoryId);

            if (spaceChanged) {
                Long newDeptId = determineDepartmentId(finalSpaceType, finalSpaceId, operator);
                file.setDepartmentId(newDeptId);
                clearCommentMentionsOnSpaceChange(fileId);
            }

            if (spaceChanged && file.getFilePath() != null) {
                String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
                if ("minio".equalsIgnoreCase(storageType)) {
                    // MinIO 文件：使用 copyObject 复制到新路径，再删除旧对象
                    try {
                        String ext = file.getFileType();
                        String newStoredName = java.util.UUID.randomUUID().toString() + (ext != null && !ext.isEmpty() ? "." + ext : "");
                        Long targetDeptForPath = file.getDepartmentId() != null ? file.getDepartmentId() : 0L;
                        String newObjectName = "departments/" + targetDeptForPath + "/files/" + newStoredName;
                        storageRouter.copy(storageType, file.getFilePath(), newObjectName);
                        storageRouter.delete(storageType, file.getFilePath());
                        file.setFilePath(newObjectName);
                    } catch (Exception e) {
                        System.err.println("[移动] MinIO 文件移动失败, sourceObject=" + file.getFilePath() + ", error=" + e.getMessage());
                    }
                } else {
                    // 本地文件：保持原有逻辑
                    java.io.File sourcePhysicalFile = new java.io.File(file.getFilePath());
                    if (sourcePhysicalFile.exists()) {
                        try {
                            Long targetDeptForPath = file.getDepartmentId() != null ? file.getDepartmentId() : 0L;
                            java.io.File targetDir = new java.io.File(uploadDir, "departments" + java.io.File.separator + targetDeptForPath + java.io.File.separator + "files");
                            if (!targetDir.exists()) {
                                targetDir.mkdirs();
                            }
                            String newStoredName = java.util.UUID.randomUUID().toString();
                            String ext = file.getFileType();
                            if (ext != null && !ext.isEmpty()) {
                                newStoredName += "." + ext;
                            }
                            java.io.File targetPhysicalFile = new java.io.File(targetDir, newStoredName);
                            cn.hutool.core.io.FileUtil.move(sourcePhysicalFile, targetPhysicalFile, true);
                            file.setFilePath(targetPhysicalFile.getAbsolutePath());
                        } catch (Exception e) {
                            System.err.println("[移动] 物理文件移动失败, sourceFile=" + file.getFilePath() + ", error=" + e.getMessage());
                        }
                    } else {
                        System.err.println("[移动] 源物理文件不存在, filePath=" + file.getFilePath());
                    }
                }
                if ("COMPLETED".equals(file.getPreviewStatus()) && file.getPreviewPdfPath() != null) {
                    java.io.File previewPdf = new java.io.File(file.getPreviewPdfPath());
                    if (!previewPdf.exists()) {
                        file.setPreviewStatus("NOT_STARTED");
                        file.setPreviewPdfPath(null);
                        // 清除 Redis 预览状态缓存，避免前端命中旧 COMPLETED 状态
                        if (stringRedisTemplate != null) {
                            try { stringRedisTemplate.delete("doc:preview:status:" + file.getId()); } catch (Exception ignored) {}
                        }
                    }
                }
            }

            DocFile saved = fileRepository.save(file);

            fileListCacheService.evictDirectory(oldDirectoryId);
            fileListCacheService.evictSpace(oldSpaceType, oldSpaceId, oldDirectoryId);
            fileListCacheService.evictDirectory(targetDirectoryId);
            fileListCacheService.evictSpace(finalSpaceType, finalSpaceId, targetDirectoryId);

            if (docFileIndexService != null) {
                try {
                    docFileIndexService.syncDocument(saved);
                } catch (Exception e) {
                    System.err.println("[移动] ES索引同步失败, fileId=" + fileId);
                }
            }

            results.add(toDTO(saved));
        }
        return results;
    }

    private void clearCommentMentionsOnSpaceChange(Long fileId) {
        List<DocComment> comments = docCommentRepository.findByFileIdAndDeletedOrderByCreateTimeDesc(fileId, 0);
        for (DocComment comment : comments) {
            if (comment.getMentions() != null && !comment.getMentions().isEmpty()) {
                comment.setMentions(null);
                docCommentRepository.save(comment);
            }
        }
        sysNotificationRepository.deleteByFileIdAndType(fileId, "MENTION");
    }

    @Transactional
    public List<FileDTO> copyFiles(List<Long> fileIds, Long targetDirectoryId, Integer targetSpaceType, Long targetSpaceId, Long userId) {
        List<FileDTO> results = new ArrayList<>();
        SysUser operator = userId != null ? sysUserRepository.findById(userId).orElse(null) : null;

        if (targetSpaceType != null && targetSpaceId != null && operator != null) {
            validateCrossSpaceCopy(operator, targetSpaceType, targetSpaceId);
        }

        for (Long fileId : fileIds) {
            DocFile sourceFile = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件不存在, id=" + fileId));

            DocFileVersion sourceLatestVersion = fileVersionRepository.findTopByFileIdOrderByVersionDesc(fileId);
            int sourceVersionNum = sourceLatestVersion != null ? sourceLatestVersion.getVersion() : (sourceFile.getVersion() != null ? sourceFile.getVersion() : 1);

            Integer finalSpaceType = targetSpaceType != null ? targetSpaceType : sourceFile.getSpaceType();
            Long finalSpaceId = targetSpaceId != null ? targetSpaceId : sourceFile.getSpaceId();

            Long targetDeptId = determineDepartmentId(finalSpaceType, finalSpaceId, operator);

            String resolvedFileName = resolveFileNameConflict(sourceFile.getFileName(), targetDirectoryId, finalSpaceType, finalSpaceId, null);

            DocFile newFile = new DocFile();
            newFile.setFileName(resolvedFileName);
            newFile.setFileType(sourceFile.getFileType());
            newFile.setFileSize(sourceFile.getFileSize());
            newFile.setStorageType(sourceFile.getStorageType());
            newFile.setMd5(sourceFile.getMd5());
            newFile.setDirectoryId(targetDirectoryId);
            newFile.setSpaceType(finalSpaceType);
            newFile.setSpaceId(finalSpaceId);
            newFile.setUploaderId(userId);
            newFile.setUploaderName(operator != null ? operator.getRealName() : sourceFile.getUploaderName());
            newFile.setVersion(1);
            newFile.setViewCount(0);
            newFile.setDownloadCount(0);
            newFile.setStatus(1);
            newFile.setDeleted(0);
            newFile.setDepartmentId(targetDeptId);

            if (sourceFile.getFulltextContent() != null) {
                newFile.setFulltextContent(sourceFile.getFulltextContent());
            }

            boolean crossSpace = (targetSpaceType != null && !targetSpaceType.equals(sourceFile.getSpaceType()))
                    || (targetSpaceId != null && !targetSpaceId.equals(sourceFile.getSpaceId()));

            if (crossSpace && sourceFile.getFilePath() != null) {
                String storageType = sourceFile.getStorageType() != null ? sourceFile.getStorageType() : "local";
                if ("minio".equalsIgnoreCase(storageType)) {
                    // MinIO 文件：使用 copyObject 复制到新对象名
                    try {
                        String ext = sourceFile.getFileType();
                        String newStoredName = java.util.UUID.randomUUID().toString() + (ext != null && !ext.isEmpty() ? "." + ext : "");
                        Long targetDeptForPath = targetDeptId != null ? targetDeptId : 0L;
                        String newObjectName = "departments/" + targetDeptForPath + "/files/" + newStoredName;
                        storageRouter.copy(storageType, sourceFile.getFilePath(), newObjectName);
                        newFile.setFilePath(newObjectName);
                        newFile.setStorageType("minio");
                    } catch (Exception e) {
                        System.err.println("[复制] MinIO 文件复制失败, sourceObject=" + sourceFile.getFilePath() + ", error=" + e.getMessage());
                        newFile.setFilePath(sourceFile.getFilePath());
                        newFile.setStorageType(sourceFile.getStorageType());
                    }
                } else {
                    // 本地文件：保持原有逻辑
                    java.io.File sourcePhysicalFile = new java.io.File(sourceFile.getFilePath());
                    if (sourcePhysicalFile.exists()) {
                        try {
                            Long targetDeptForPath = targetDeptId != null ? targetDeptId : 0L;
                            java.io.File targetDir = new java.io.File(uploadDir, "departments" + java.io.File.separator + targetDeptForPath + java.io.File.separator + "files");
                            if (!targetDir.exists()) {
                                targetDir.mkdirs();
                            }
                            String newStoredName = java.util.UUID.randomUUID().toString();
                            String ext = sourceFile.getFileType();
                            if (ext != null && !ext.isEmpty()) {
                                newStoredName += "." + ext;
                            }
                            java.io.File targetPhysicalFile = new java.io.File(targetDir, newStoredName);
                            cn.hutool.core.io.FileUtil.copy(sourcePhysicalFile, targetPhysicalFile, true);
                            newFile.setFilePath(targetPhysicalFile.getAbsolutePath());
                        } catch (Exception e) {
                            System.err.println("[复制] 物理文件复制失败, sourceFile=" + sourceFile.getFilePath() + ", error=" + e.getMessage());
                            newFile.setFilePath(sourceFile.getFilePath());
                        }
                    } else {
                        newFile.setFilePath(sourceFile.getFilePath());
                    }
                }
            } else {
                newFile.setFilePath(sourceFile.getFilePath());
            }

            DocFile saved = fileRepository.save(newFile);

            if ("COMPLETED".equals(sourceFile.getPreviewStatus()) && sourceFile.getPreviewPdfPath() != null) {
                java.io.File sourcePdf = new java.io.File(sourceFile.getPreviewPdfPath());
                if (sourcePdf.exists()) {
                    try {
                        java.io.File cacheDir = new java.io.File(uploadDir, "pdf_cache");
                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs();
                        }
                        java.io.File targetPdf = new java.io.File(cacheDir, saved.getId() + ".pdf");
                        cn.hutool.core.io.FileUtil.copy(sourcePdf, targetPdf, true);
                        saved.setPreviewStatus("COMPLETED");
                        saved.setPreviewPdfPath(targetPdf.getAbsolutePath());
                        saved = fileRepository.save(saved);
                    } catch (Exception e) {
                        System.err.println("[复制] 预览缓存复制失败, fileId=" + saved.getId() + ", error=" + e.getMessage());
                    }
                }
            }

            DocFileVersion version = new DocFileVersion();
            version.setFileId(saved.getId());
            version.setVersion(1);
            version.setFilePath(saved.getFilePath());
            version.setFileSize(sourceFile.getFileSize());
            version.setOperatorId(userId);
            version.setChangeNote("复制自 " + sourceFile.getFileName() + " v" + sourceVersionNum);
            fileVersionRepository.save(version);

            fileListCacheService.evictDirectory(targetDirectoryId);
            fileListCacheService.evictSpace(finalSpaceType, finalSpaceId, targetDirectoryId);

            if (docFileIndexService != null) {
                try {
                    docFileIndexService.syncDocument(saved);
                } catch (Exception e) {
                    System.err.println("[复制] ES索引同步失败, fileId=" + saved.getId());
                }
            }

            results.add(toDTO(saved));
        }
        return results;
    }

    private void validateCrossSpaceCopy(SysUser operator, Integer targetSpaceType, Long targetSpaceId) {
        if (targetSpaceType == 0) {
            if (!targetSpaceId.equals(operator.getId())) {
                throw new RuntimeException("无权复制到他人的个人空间");
            }
        } else if (targetSpaceType == 1) {
            if (operator.getDepartmentId() == null || !operator.getDepartmentId().equals(targetSpaceId)) {
                throw new RuntimeException("无权复制到非本部门的部门空间");
            }
        } else if (targetSpaceType == 2) {
            if (operator.getDepartmentId() == null) {
                throw new RuntimeException("您尚未分配部门，无法访问企业空间");
            }
        }
    }

    private Long determineDepartmentId(Integer spaceType, Long spaceId, SysUser operator) {
        if (spaceType != null && spaceType == 1 && spaceId != null) {
            return spaceId;
        }
        if (operator != null && operator.getDepartmentId() != null && (spaceType == null || spaceType == 0)) {
            return operator.getDepartmentId();
        }
        return null;
    }

    private List<String> getRoleCodesForUser(Long userId) {
        List<Long> roleIds = sysUserRoleRepository.findByUserId(userId)
                .stream()
                .map(front.system.entity.SysUserRole::getRoleId)
                .collect(Collectors.toList());
        return roleIds.stream()
                .map(roleId -> sysRoleRepository.findById(roleId).orElse(null))
                .filter(r -> r != null)
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    private void logFileTransfer(Long operatorId, Long fileId, Long fromDeptId, Long toDeptId) {
        try {
            SysOperationLog log = new SysOperationLog();
            log.setUserId(operatorId);
            log.setOperation("FILE_TRANSFER_CROSS_DEPT");
            log.setDetail(String.format("跨部门转移文件[id=%d, 源部门=%s, 目标部门=%s]",
                    fileId, fromDeptId, toDeptId));
            log.setCreateTime(LocalDateTime.now());
            operationLogRepository.save(log);
        } catch (Exception e) {
        }
    }

    private boolean checkCrossDepartmentPermission(Long userId) {
        SysUser user = sysUserRepository.findById(userId).orElse(null);
        if (user == null) return false;

        List<Long> roleIds = sysUserRoleRepository.findByUserId(userId)
                .stream()
                .map(front.system.entity.SysUserRole::getRoleId)
                .collect(Collectors.toList());

        for (Long roleId : roleIds) {
            SysRole role = sysRoleRepository.findById(roleId).orElse(null);
            if (role != null) {
                String code = role.getRoleCode();
                if ("ROLE_ADMIN".equals(code) || "ROLE_DEPT_ADMIN".equals(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    private FileDTO toDTO(DocFile file) {
        FileDTO dto = new FileDTO();
        dto.setId(file.getId());
        dto.setFileName(file.getFileName());
        dto.setFileType(file.getFileType());
        dto.setFileSize(file.getFileSize());
        dto.setFilePath(file.getFilePath());
        dto.setStorageType(file.getStorageType());
        dto.setMd5(file.getMd5());
        dto.setDirectoryId(file.getDirectoryId());
        dto.setDepartmentId(file.getDepartmentId());
        dto.setSpaceType(file.getSpaceType());
        dto.setSpaceId(file.getSpaceId());
        dto.setUploaderId(file.getUploaderId());
        dto.setUploaderName(file.getUploaderName());
        dto.setVersion(file.getVersion());
        dto.setViewCount(file.getViewCount());
        dto.setDownloadCount(file.getDownloadCount());
        dto.setStatus(file.getStatus());
        dto.setPreviewStatus(file.getPreviewStatus());
        dto.setPreviewPdfPath(file.getPreviewPdfPath());
        dto.setCreateTime(file.getCreateTime());
        dto.setUpdateTime(file.getUpdateTime());
        return dto;
    }
}
