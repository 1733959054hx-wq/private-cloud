package front.workspace.documentspace.service;

import front.intelligence.text.service.FulltextExtractService;
import front.intelligence.ocr.repository.OcrRecordRepository;
import front.intelligence.ai.repository.DocMetadataRepository;
import front.intelligence.ai.repository.DocTagRepository;
import front.workspace.documentspace.dto.VersionDTO;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.entity.DocFileVersion;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.documentspace.repository.DocFileVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileVersionService {

    /** 预览状态 Redis 缓存前缀，必须与 PreviewController 中保持一致 */
    private static final String PREVIEW_STATUS_PREFIX = "doc:preview:status:";

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Autowired
    private DocFileVersionRepository fileVersionRepository;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private FulltextExtractService fulltextExtractService;

    @Autowired
    private OcrRecordRepository ocrRecordRepository;

    @Autowired
    private DocMetadataRepository docMetadataRepository;

    @Autowired
    private DocTagRepository docTagRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private front.storage.service.StorageRouter storageRouter;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 清除文件预览状态的 Redis 缓存。
     * 上传新版本/回滚版本后，数据库 previewStatus 已重置为 NOT_STARTED，
     * 但 Redis 中仍缓存旧的 COMPLETED 状态，会导致前端拿到旧状态去请求已失效的 converted-pdf，
     * 进而因 404 白屏。必须在事务提交后清除缓存。
     */
    private void evictPreviewStatusCache(Long fileId) {
        try {
            stringRedisTemplate.delete(PREVIEW_STATUS_PREFIX + fileId);
        } catch (Exception ignored) {
            // Redis 不可用时降级，不影响主流程
        }
    }

    @Transactional
    public void resetFileSmartData(Long fileId) {
        ocrRecordRepository.deleteByFileId(fileId);
        docMetadataRepository.deleteByFileId(fileId);
        docTagRepository.deleteByFileId(fileId);
        // 显式刷新删除操作到数据库，并清除JPA一级缓存
        entityManager.flush();
        entityManager.clear();
    }

    public List<VersionDTO> getVersionHistory(Long fileId) {
        List<DocFileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionDesc(fileId);
        return versions.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public VersionDTO addVersion(Long fileId, MultipartFile file, Long operatorId, String changeNote) throws IOException {
        DocFile docFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        // 文件类型一致性校验
        String newExt = getExtension(file.getOriginalFilename());
        if (docFile.getFileType() != null && !docFile.getFileType().equalsIgnoreCase(newExt)) {
            throw new RuntimeException("上传失败：新版本的文件类型必须与原文件一致（应为 " + docFile.getFileType() + " 格式）");
        }

        // 清理旧版本的智能数据
        resetFileSmartData(fileId);

        // resetFileSmartData 中使用了 entityManager.flush() + clear()
        // 持久化上下文被清除，需要重新读取实体以获取托管状态
        docFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        DocFileVersion latestVersion = fileVersionRepository.findTopByFileIdOrderByVersionDesc(fileId);
        int newVersionNum = (latestVersion != null) ? latestVersion.getVersion() + 1 : 1;

        String ext = getExtension(docFile.getFileName());
        String storedName = docFile.getMd5() + "_v" + newVersionNum + (ext.isEmpty() ? "" : "." + ext);
        Long deptId = docFile.getDepartmentId() != null ? docFile.getDepartmentId() : 0L;
        // 关键修复：使用绝对路径，避免相对路径在工作目录变化时找不到文件
        Path finalPath = Paths.get(uploadDir, "departments", String.valueOf(deptId), "versions", storedName)
                .toAbsolutePath().normalize();
        Files.createDirectories(finalPath.getParent());
        Files.copy(file.getInputStream(), finalPath, StandardCopyOption.REPLACE_EXISTING);

        DocFileVersion version = new DocFileVersion();
        version.setFileId(fileId);
        version.setVersion(newVersionNum);
        version.setFilePath(finalPath.toString());
        version.setFileSize(file.getSize());
        version.setOperatorId(operatorId);
        version.setChangeNote(changeNote != null ? changeNote : "版本更新");
        DocFileVersion saved = fileVersionRepository.save(version);

        docFile.setVersion(newVersionNum);
        docFile.setFilePath(finalPath.toString());
        docFile.setFileSize(file.getSize());
        // 关键修复：addVersion 用 Files.copy 存到本地，必须显式设置 storageType=local。
        // 否则若原文件 storageType=minio，新版本文件实际在本地但标记为 minio，
        // 导致预览转换时去 MinIO 下载本地路径而失败。
        docFile.setStorageType("local");
        docFile.setPreviewStatus("NOT_STARTED"); // 重置预览状态，让前端重新触发转换
        docFile.setPreviewPdfPath(null); // 清除旧的转换PDF路径
        fileRepository.save(docFile);

        // 文本提取在事务提交后异步执行，避免阻塞响应
        final Long fileIdRef = fileId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 关键修复：事务提交后清除 Redis 预览状态缓存。
                    // 否则前端 getConvertStatus 会命中旧缓存（COMPLETED），
                    // 去请求已失效的 converted-pdf 导致 404 白屏。
                    evictPreviewStatusCache(fileIdRef);
                    new Thread(() -> {
                        try {
                            fulltextExtractService.extractAndSave(fileIdRef);
                            System.out.println("[文本提取] 版本更新后自动提取完成, fileId=" + fileIdRef);
                        } catch (Exception e) {
                            System.err.println("[文本提取] 版本更新后自动提取失败, fileId=" + fileIdRef + ", error=" + e.getMessage());
                        }
                    }, "version-extract").start();
                }
            });
        }

        return toDTO(saved);
    }

    @Transactional
    public VersionDTO rollbackVersion(Long fileId, Integer targetVersion) {
        DocFileVersion targetVer = fileVersionRepository.findByFileIdOrderByVersionDesc(fileId).stream()
                .filter(v -> v.getVersion().equals(targetVersion))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("目标版本不存在"));

        DocFile docFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        // 关键修复：回滚前校验目标版本文件是否存在，避免回滚后预览白屏
        String rawPath = targetVer.getFilePath();
        if (rawPath == null || rawPath.isEmpty()) {
            throw new RuntimeException("目标版本文件路径为空，无法回滚");
        }

        // 保留原始路径用于降级查找（未规范化的相对路径）
        String currentStorageType = docFile.getStorageType() != null ? docFile.getStorageType() : "local";
        String resolvedPath = rawPath;
        boolean found = false;

        // 策略1：直接用 storageRouter 检查原始路径（支持 MinIO 和本地绝对路径）
        if (storageRouter.exists(currentStorageType, rawPath)) {
            found = true;
            // 如果是本地路径，规范化为绝对路径
            if (currentStorageType.equalsIgnoreCase("local") || looksLikeLocalPath(rawPath)) {
                try {
                    resolvedPath = Paths.get(rawPath).toAbsolutePath().normalize().toString();
                } catch (Exception ignored) { }
            }
        }

        // 策略2：尝试 uploads/<rawPath>（兼容 filePath 缺少 uploads 前缀的历史数据）
        if (!found) {
            java.io.File localCandidate = new java.io.File(uploadDir, rawPath);
            if (localCandidate.exists()) {
                resolvedPath = localCandidate.getAbsolutePath();
                currentStorageType = "local";
                found = true;
            }
        }

        // 策略3：规范化为绝对路径后检查（兼容 filePath 为相对路径但不在 uploads 下）
        if (!found) {
            try {
                String absPath = Paths.get(rawPath).toAbsolutePath().normalize().toString();
                if (new java.io.File(absPath).exists()) {
                    resolvedPath = absPath;
                    currentStorageType = "local";
                    found = true;
                }
            } catch (Exception ignored) { }
        }

        if (!found) {
            throw new RuntimeException("目标版本文件不存在（可能已被清理），无法回滚到 V" + targetVersion + "。路径: " + rawPath);
        }

        docFile.setVersion(targetVersion);
        docFile.setFilePath(resolvedPath);
        docFile.setFileSize(targetVer.getFileSize());
        docFile.setStorageType(currentStorageType);
        docFile.setPreviewStatus("NOT_STARTED"); // 重置预览状态，让前端重新触发转换
        docFile.setPreviewPdfPath(null); // 清除旧的转换PDF路径
        fileRepository.save(docFile);

        // 关键修复：回滚版本同样需要清除 Redis 预览状态缓存，否则前端会命中旧缓存导致白屏
        final Long fileIdRef = fileId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictPreviewStatusCache(fileIdRef);
                }
            });
        } else {
            // 无事务上下文时直接清除
            evictPreviewStatusCache(fileId);
        }

        return toDTO(targetVer);
    }

    /**
     * 判断路径是否为本地文件系统路径（与 StorageRouter 中逻辑一致）
     */
    private boolean looksLikeLocalPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        if (filePath.indexOf('\\') >= 0) return true;
        if (filePath.startsWith("./") || filePath.startsWith(".\\")) return true;
        if (filePath.length() >= 2 && filePath.charAt(1) == ':') return true;
        if (filePath.contains("uploads")) return true;
        return false;
    }

    private VersionDTO toDTO(DocFileVersion v) {
        VersionDTO dto = new VersionDTO();
        dto.setId(v.getId());
        dto.setFileId(v.getFileId());
        dto.setVersion(v.getVersion());
        dto.setFilePath(v.getFilePath());
        dto.setFileSize(v.getFileSize());
        dto.setOperatorId(v.getOperatorId());
        dto.setChangeNote(v.getChangeNote());
        dto.setCreateTime(v.getCreateTime());
        return dto;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
