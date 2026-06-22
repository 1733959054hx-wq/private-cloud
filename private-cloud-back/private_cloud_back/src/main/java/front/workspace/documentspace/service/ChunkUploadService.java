package front.workspace.documentspace.service;

import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.entity.DocFileVersion;
import front.workspace.documentspace.entity.DocUploadTask;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.documentspace.repository.DocFileVersionRepository;
import front.workspace.documentspace.repository.DocUploadTaskRepository;
import front.intelligence.ocr.service.OcrService;
import front.intelligence.text.service.FulltextExtractService;
import front.intelligence.ocr.repository.OcrRecordRepository;
import front.intelligence.ai.repository.DocMetadataRepository;
import front.intelligence.ai.repository.DocTagRepository;
import front.storage.service.StorageRouter;
import front.mq.config.MqConstants;
import front.mq.service.TaskPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class ChunkUploadService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Autowired
    private DocUploadTaskRepository uploadTaskRepository;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private DocFileVersionRepository fileVersionRepository;

    @Autowired
    private OcrService ocrService;

    @Autowired
    private FulltextExtractService fulltextExtractService;

    @Autowired
    private front.system.repository.SysUserRepository sysUserRepository;

    @Autowired
    private OcrRecordRepository ocrRecordRepository;

    @Autowired
    private DocMetadataRepository docMetadataRepository;

    @Autowired
    private DocTagRepository docTagRepository;

    @Autowired
    private FileListCacheService fileListCacheService;

    @Autowired
    private ChunkProgressCacheService chunkProgressCacheService;

    @Autowired
    private StorageRouter storageRouter;

    @Autowired
    private TaskPublisher taskPublisher;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /** 预览状态 Redis 缓存前缀，与 PreviewController 保持一致 */
    private static final String PREVIEW_STATUS_PREFIX = "doc:preview:status:";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 清除文件预览状态的 Redis 缓存。
     * 覆盖上传/秒传重置 previewStatus 后必须调用，否则前端会命中旧缓存导致白屏。
     */
    private void evictPreviewStatusCache(Long fileId) {
        try {
            stringRedisTemplate.delete(PREVIEW_STATUS_PREFIX + fileId);
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public Map<String, Object> createUploadTask(String fileId, String fileName, Integer totalChunks,
                                                  Long fileSize, Long directoryId, Long departmentId, Long uploaderId,
                                                  String mode, Long updateFileId, Integer spaceType, Long spaceId) {
        Optional<DocUploadTask> existingTask = uploadTaskRepository.findByFileIdAndStatus(fileId, 0);
        if (existingTask.isPresent()) {
            DocUploadTask task = existingTask.get();
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", task.getId());
            result.put("fileId", task.getFileId());
            result.put("receivedChunks", task.getReceivedChunks());
            result.put("totalChunks", task.getTotalChunks());
            result.put("status", "RESUME");
            chunkProgressCacheService.warmupProgress(
                    task.getFileId(), task.getTotalChunks(), task.getUploaderId(),
                    task.getFileName(), null);
            return result;
        }

        DocUploadTask task = new DocUploadTask();
        task.setFileId(fileId);
        task.setFileName(fileName);
        task.setTotalChunks(totalChunks);
        task.setFileSize(fileSize);
        task.setReceivedChunks(0);
        task.setStatus(0);
        task.setUploaderId(uploaderId);
        task.setDirectoryId(directoryId);
        task.setDepartmentId(departmentId);
        task.setMode(mode);
        task.setUpdateFileId(updateFileId);
        task.setSpaceType(spaceType);
        task.setSpaceId(spaceId);
        DocUploadTask saved = uploadTaskRepository.save(task);

        chunkProgressCacheService.initProgress(
                saved.getFileId(), saved.getTotalChunks(), saved.getUploaderId(), saved.getFileName());

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", saved.getId());
        result.put("fileId", saved.getFileId());
        result.put("receivedChunks", 0);
        result.put("totalChunks", totalChunks);
        result.put("status", "NEW");
        return result;
    }

    /**
     * 上传分片。当所有分片上传完毕时，同步执行合并操作。
     * 合并（文件I/O + 数据库写入）在事务内同步完成，确保数据可靠。
     * OCR 和文本提取在事务提交后异步执行，不阻塞最后一个分片的响应。
     */
    @Transactional
    public Map<String, Object> uploadChunk(String fileId, Integer chunkIndex, MultipartFile chunkFile,
                                            Long uploaderId) throws IOException {
        DocUploadTask task = uploadTaskRepository.findByFileIdAndUploaderIdAndStatus(fileId, uploaderId, 0)
                .orElseThrow(() -> new RuntimeException("上传任务不存在或已完成"));

        Path chunkDir = Paths.get(uploadDir, "chunks", fileId);
        Files.createDirectories(chunkDir);
        Path chunkPath = chunkDir.resolve(String.valueOf(chunkIndex));
        Files.copy(chunkFile.getInputStream(), chunkPath, StandardCopyOption.REPLACE_EXISTING);

        // 使用原子递增，避免并发导致 receivedChunks 计数不准确
        uploadTaskRepository.incrementReceivedChunks(task.getId());
        // 重新读取以获取最新的 receivedChunks
        task = uploadTaskRepository.findById(task.getId()).orElseThrow();

        // 同步把分片接收状态写入 Redis Hash（断点续传时优先读缓存）
        chunkProgressCacheService.markChunkReceived(fileId, chunkIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("chunkIndex", chunkIndex);
        result.put("receivedChunks", task.getReceivedChunks());
        result.put("totalChunks", task.getTotalChunks());

        if (task.getReceivedChunks().equals(task.getTotalChunks())) {
            // 同步执行合并操作（文件I/O + 数据库写入），确保事务生效
            DocFile mergedFile = doMergeChunks(task.getId());
            // 上传完成，清理进度缓存
            chunkProgressCacheService.evictProgress(fileId);
            result.put("status", "MERGED");
            result.put("fileRecordId", mergedFile.getId());

            // OCR 和文本提取在事务提交后异步执行，避免阻塞最后一个分片的响应
            // 优先走 MQ，MQ 不可用时降级为线程池执行
            final Long mergedFileId = mergedFile.getId();
            registerPostCommitAction(() -> {
                // OCR 任务
                String ocrTaskId = taskPublisher.publish(MqConstants.TASK_FILE_OCR, mergedFileId, null);
                if (ocrTaskId == null) {
                    // MQ 不可用，降级为线程池执行
                    try {
                        ocrService.triggerOcrAsync(mergedFileId);
                    } catch (Exception e) {
                        System.err.println("[OCR] 降级执行失败, fileId=" + mergedFileId + ", error=" + e.getMessage());
                    }
                }
                // 全文提取任务
                String fulltextTaskId = taskPublisher.publish(MqConstants.TASK_FILE_FULLTEXT, mergedFileId, null);
                if (fulltextTaskId == null) {
                    // MQ 不可用，降级为线程池执行
                    try {
                        fulltextExtractService.extractAndSave(mergedFileId);
                        System.out.println("[文本提取] 降级执行完成, fileId=" + mergedFileId);
                    } catch (Exception e) {
                        System.err.println("[文本提取] 降级执行失败, fileId=" + mergedFileId + ", error=" + e.getMessage());
                    }
                }
            });
        } else {
            result.put("status", "UPLOADING");
        }

        return result;
    }

    /**
     * 断点续传查询：先查缓存，缓存未命中再查数据库
     */
    public Map<String, Object> queryResumeUpload(String fileId, Long uploaderId) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> cached = chunkProgressCacheService.getResumeInfo(fileId);
        if (cached != null) {
            Object uploaderObj = cached.get("uploaderId");
            if (uploaderObj != null && Long.valueOf(uploaderObj.toString()).equals(uploaderId)) {
                result.put("fileId", fileId);
                result.put("receivedChunks", cached.get("receivedChunks"));
                result.put("totalChunks", cached.get("totalChunks"));
                result.put("status", "RESUME");
                return result;
            }
        }

        Optional<DocUploadTask> taskOpt = uploadTaskRepository.findByFileIdAndUploaderIdAndStatus(fileId, uploaderId, 0);
        if (taskOpt.isPresent()) {
            DocUploadTask task = taskOpt.get();
            result.put("taskId", task.getId());
            result.put("fileId", task.getFileId());
            result.put("receivedChunks", task.getReceivedChunks());
            result.put("totalChunks", task.getTotalChunks());
            result.put("status", "RESUME");
            chunkProgressCacheService.warmupProgress(
                    task.getFileId(), task.getTotalChunks(), task.getUploaderId(),
                    task.getFileName(), null);
            return result;
        }

        result.put("status", "NOT_FOUND");
        return result;
    }

    /**
     * 注册事务提交后执行的回调（在独立线程中运行，不阻塞事务提交）
     */
    private void registerPostCommitAction(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    new Thread(action, "post-commit-task").start();
                }
            });
        } else {
            // 没有事务上下文时直接在新线程执行
            new Thread(action, "post-commit-task").start();
        }
    }

    /**
     * 合并分片（内部方法，由 uploadChunk 同步调用，确保在同一事务中）
     */
    private DocFile doMergeChunks(Long taskId) throws IOException {
        DocUploadTask task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("上传任务不存在, taskId=" + taskId));
        // 版本更新模式：合并分片后插入新的文件版本
        if ("updateVersion".equals(task.getMode()) && task.getUpdateFileId() != null) {
            return doMergeChunksAsNewVersion(task);
        }

        // 普通上传模式：合并分片后创建新文件
        String fileName = task.getFileName();
        Long directoryId = task.getDirectoryId();
        boolean nameExists;
        if (directoryId != null) {
            nameExists = fileRepository.existsByFileNameAndDirectoryIdAndDeleted(fileName, directoryId, 0);
        } else {
            nameExists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndDeleted(fileName, 0);
        }
        if (nameExists) {
            String baseName = fileName;
            String ext = "";
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) {
                baseName = fileName.substring(0, dotIdx);
                ext = fileName.substring(dotIdx);
            }
            int counter = 1;
            String newName = baseName + " (" + counter + ")" + ext;
            while (true) {
                if (directoryId != null) {
                    nameExists = fileRepository.existsByFileNameAndDirectoryIdAndDeleted(newName, directoryId, 0);
                } else {
                    nameExists = fileRepository.existsByFileNameAndDirectoryIdIsNullAndDeleted(newName, 0);
                }
                if (!nameExists) break;
                counter++;
                newName = baseName + " (" + counter + ")" + ext;
            }
            fileName = newName;
        }

        Path chunkDir = Paths.get(uploadDir, "chunks", task.getFileId());
        String ext = getExtension(task.getFileName());
        String storedName = task.getFileId() + (ext.isEmpty() ? "" : "." + ext);
        Long deptId = task.getDepartmentId();
        if (deptId == null) deptId = 0L;
        Path finalPath = Paths.get(uploadDir, "departments", String.valueOf(deptId), "files", storedName);
        Files.createDirectories(finalPath.getParent());

        mergeChunkFiles(chunkDir, task.getTotalChunks(), finalPath);
        cleanupChunkFiles(chunkDir, task.getTotalChunks());

        // 决定存储方式：若启用 MinIO 且文件大小满足阈值，则上传到 MinIO
        String storageType = "local";
        String filePath = finalPath.toString();
        long fileSize = task.getFileSize() != null ? task.getFileSize() : 0L;
        if (storageRouter.shouldUseMinio(fileSize)) {
            String objectName = "departments/" + deptId + "/files/" + storedName;
            try (FileInputStream fis = new FileInputStream(finalPath.toFile())) {
                StorageRouter.UploadResult uploadResult = storageRouter.upload(
                        fis, objectName, finalPath.toString(), fileSize, null);
                storageType = uploadResult.getStorageType();
                filePath = uploadResult.getPath();
                // 上传到 MinIO 后删除本地临时文件
                if ("minio".equals(storageType)) {
                    try { Files.deleteIfExists(finalPath); } catch (IOException ignored) {}
                }
            } catch (StorageRouter.StorageFallbackException e) {
                // MinIO 上传失败，降级使用本地存储
                System.err.println("[上传] MinIO 降级到本地存储, fileId=" + task.getFileId() + ", error=" + e.getMessage());
                storageType = "local";
                filePath = finalPath.toString();
            } catch (Exception e) {
                System.err.println("[上传] MinIO 上传异常, 降级到本地存储, fileId=" + task.getFileId() + ", error=" + e.getMessage());
                storageType = "local";
                filePath = finalPath.toString();
            }
        }

        DocFile docFile = new DocFile();
        docFile.setFileName(fileName);
        docFile.setFileType(ext);
        docFile.setFileSize(task.getFileSize());
        docFile.setFilePath(filePath);
        docFile.setStorageType(storageType);
        docFile.setMd5(task.getFileId());
        docFile.setDirectoryId(task.getDirectoryId());
        docFile.setDepartmentId(task.getDepartmentId());
        docFile.setSpaceType(task.getSpaceType() != null ? task.getSpaceType() : 0);
        docFile.setSpaceId(task.getSpaceId() != null ? task.getSpaceId() : task.getUploaderId());
        docFile.setUploaderId(task.getUploaderId());
        sysUserRepository.findById(task.getUploaderId()).ifPresent(user ->
                docFile.setUploaderName(user.getRealName() != null ? user.getRealName() : user.getUsername()));
        docFile.setVersion(1);
        docFile.setStatus(1);
        docFile.setDeleted(0);
        DocFile saved = fileRepository.save(docFile);

        DocFileVersion version = new DocFileVersion();
        version.setFileId(saved.getId());
        version.setVersion(1);
        version.setFilePath(saved.getFilePath());
        version.setFileSize(saved.getFileSize());
        version.setOperatorId(task.getUploaderId());
        version.setChangeNote("初始上传");
        fileVersionRepository.save(version);

        task.setStatus(1);
        uploadTaskRepository.save(task);

        fileListCacheService.evictDirectory(saved.getDirectoryId());
        fileListCacheService.evictSpace(saved.getSpaceType(), saved.getSpaceId(), saved.getDirectoryId());

        return saved;
    }

    /**
     * 版本更新模式：合并分片后插入新的文件版本（DocFileVersion），而非新建文件
     */
    private DocFile doMergeChunksAsNewVersion(DocUploadTask task) throws IOException {
        Long existingFileId = task.getUpdateFileId();
        DocFile existingFile = fileRepository.findById(existingFileId)
                .orElseThrow(() -> new RuntimeException("原文件不存在，fileId=" + existingFileId));

        // 文件类型一致性校验
        String newExt = getExtension(task.getFileName());
        if (existingFile.getFileType() != null && !existingFile.getFileType().equalsIgnoreCase(newExt)) {
            throw new RuntimeException("上传失败：新版本的文件类型必须与原文件一致（应为 " + existingFile.getFileType() + " 格式）");
        }

        // 清理旧版本的智能数据（OCR记录、AI标签、AI元数据）
        ocrRecordRepository.deleteByFileId(existingFileId);
        docMetadataRepository.deleteByFileId(existingFileId);
        docTagRepository.deleteByFileId(existingFileId);

        // 显式刷新删除操作到数据库，并清除JPA一级缓存
        // 确保后续读取到的是数据库最新状态
        entityManager.flush();
        entityManager.clear();

        // 重新读取实体以获取托管状态
        Long taskId = task.getId();
        existingFile = fileRepository.findById(existingFileId)
                .orElseThrow(() -> new RuntimeException("原文件不存在，fileId=" + existingFileId));
        task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("上传任务不存在, taskId=" + taskId));

        // 合并分片到新路径
        Path chunkDir = Paths.get(uploadDir, "chunks", task.getFileId());
        String ext = getExtension(task.getFileName());
        String storedName = task.getFileId() + (ext.isEmpty() ? "" : "." + ext);
        Long deptId = existingFile.getDepartmentId() != null ? existingFile.getDepartmentId() : 0L;
        Path finalPath = Paths.get(uploadDir, "departments", String.valueOf(deptId), "files", storedName);
        Files.createDirectories(finalPath.getParent());

        mergeChunkFiles(chunkDir, task.getTotalChunks(), finalPath);
        cleanupChunkFiles(chunkDir, task.getTotalChunks());

        // 决定存储方式：若启用 MinIO 且文件大小满足阈值，则上传到 MinIO
        String storageType = "local";
        String filePath = finalPath.toString();
        long fileSize = task.getFileSize() != null ? task.getFileSize() : 0L;
        if (storageRouter.shouldUseMinio(fileSize)) {
            String objectName = "departments/" + deptId + "/files/" + storedName;
            try (FileInputStream fis = new FileInputStream(finalPath.toFile())) {
                StorageRouter.UploadResult uploadResult = storageRouter.upload(
                        fis, objectName, finalPath.toString(), fileSize, null);
                storageType = uploadResult.getStorageType();
                filePath = uploadResult.getPath();
                if ("minio".equals(storageType)) {
                    try { Files.deleteIfExists(finalPath); } catch (IOException ignored) {}
                }
            } catch (StorageRouter.StorageFallbackException e) {
                System.err.println("[版本上传] MinIO 降级到本地存储, fileId=" + task.getFileId() + ", error=" + e.getMessage());
                storageType = "local";
                filePath = finalPath.toString();
            } catch (Exception e) {
                System.err.println("[版本上传] MinIO 上传异常, 降级到本地存储, fileId=" + task.getFileId() + ", error=" + e.getMessage());
                storageType = "local";
                filePath = finalPath.toString();
            }
        }

        // 更新原文件信息
        int newVersion = existingFile.getVersion() != null ? existingFile.getVersion() + 1 : 2;
        existingFile.setFilePath(filePath);
        existingFile.setFileSize(task.getFileSize());
        existingFile.setFileType(ext);
        existingFile.setVersion(newVersion);
        existingFile.setStorageType(storageType);
        existingFile.setMd5(task.getFileId());
        existingFile.setFulltextContent(null); // 清除旧全文内容，后续异步重新提取
        existingFile.setPreviewStatus("NOT_STARTED"); // 重置预览状态，让前端重新触发转换
        existingFile.setPreviewPdfPath(null); // 清除旧的转换PDF路径
        fileRepository.save(existingFile);

        // 再次刷新确保文件更新写入数据库
        entityManager.flush();

        // 关键修复：清除 Redis 预览状态缓存，否则前端会命中旧 COMPLETED 缓存导致白屏
        final Long evictFileId = existingFile.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictPreviewStatusCache(evictFileId);
                }
            });
        } else {
            evictPreviewStatusCache(evictFileId);
        }

        // 插入新的文件版本记录
        DocFileVersion version = new DocFileVersion();
        version.setFileId(existingFile.getId());
        version.setVersion(newVersion);
        version.setFilePath(filePath);
        version.setFileSize(task.getFileSize());
        version.setOperatorId(task.getUploaderId());
        version.setChangeNote("分片上传新版本");
        fileVersionRepository.save(version);

        // 关键修复：归档当前（旧）版本的物理文件到 versions 目录，避免后续操作影响旧版本文件。
        // 旧版本记录的 filePath 原本指向 files/ 目录下的原文件，若不归档，
        // 后续覆盖上传/移动/删除等操作可能导致旧版本文件丢失，回滚时找不到文件。
        archiveCurrentVersionFile(existingFile, newVersion - 1);

        task.setStatus(1);
        uploadTaskRepository.save(task);

        fileListCacheService.evictDirectory(existingFile.getDirectoryId());
        fileListCacheService.evictSpace(existingFile.getSpaceType(), existingFile.getSpaceId(), existingFile.getDirectoryId());

        return existingFile;
    }

    /**
     * 合并分片文件到目标路径
     */
    private void mergeChunkFiles(Path chunkDir, int totalChunks, Path finalPath) throws IOException {
        byte[] buffer = new byte[8192];
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(finalPath.toFile())) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunkPath = chunkDir.resolve(String.valueOf(i));
                try (java.io.FileInputStream fis = new java.io.FileInputStream(chunkPath.toFile())) {
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    /**
     * 清理分片临时文件
     */
    private void cleanupChunkFiles(Path chunkDir, int totalChunks) throws IOException {
        for (int i = 0; i < totalChunks; i++) {
            Files.deleteIfExists(chunkDir.resolve(String.valueOf(i)));
        }
        Files.deleteIfExists(chunkDir);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 归档当前（旧）版本的物理文件到 versions 目录。
     * 在上传新版本时调用，确保旧版本文件有独立归档副本，不会被后续操作影响。
     * 同时更新旧版本记录的 filePath 指向归档路径。
     *
     * 注意：调用此方法时 existingFile 可能已经被更新为新版本信息，
     * 因此必须从数据库中的旧版本记录获取旧文件路径，不能直接用 existingFile.getFilePath()。
     */
    private void archiveCurrentVersionFile(DocFile existingFile, int oldVersionNum) {
        if (existingFile == null || existingFile.getId() == null) return;

        try {
            // 关键修复：从数据库旧版本记录获取旧文件路径，而不是 existingFile.getFilePath()
            // 因为 existingFile 可能已被更新为新版本的 filePath
            DocFileVersion oldVersion = fileVersionRepository.findByFileIdOrderByVersionDesc(existingFile.getId()).stream()
                    .filter(v -> v.getVersion() == oldVersionNum)
                    .findFirst().orElse(null);
            if (oldVersion == null) {
                System.err.println("[版本归档] 未找到旧版本记录, fileId=" + existingFile.getId() + ", version=" + oldVersionNum);
                return;
            }

            String oldFilePath = oldVersion.getFilePath();
            if (oldFilePath == null || oldFilePath.isEmpty()) return;

            // 判断旧文件是否在 files/ 目录（未归档），若已在 versions/ 目录则跳过
            String normalized = oldFilePath.replace('\\', '/');
            if (normalized.contains("/versions/")) return;

            // 通过 StorageRouter 读取旧文件流（兼容 local/minio）
            String storageType = existingFile.getStorageType() != null ? existingFile.getStorageType() : "local";
            if (!storageRouter.exists(storageType, oldFilePath)) {
                System.err.println("[版本归档] 旧版本文件不存在，跳过归档, filePath=" + oldFilePath);
                return;
            }

            // 构造归档路径：uploads/departments/{deptId}/versions/{md5}_v{n}.ext
            String ext = getExtension(existingFile.getFileName());
            String md5 = existingFile.getMd5() != null ? existingFile.getMd5() : java.util.UUID.randomUUID().toString();
            Long deptId = existingFile.getDepartmentId() != null ? existingFile.getDepartmentId() : 0L;
            String archivedName = md5 + "_v" + oldVersionNum + (ext.isEmpty() ? "" : "." + ext);
            Path archivePath = Paths.get(uploadDir, "departments", String.valueOf(deptId), "versions", archivedName)
                    .toAbsolutePath().normalize();
            Files.createDirectories(archivePath.getParent());

            // 复制旧文件到归档路径
            try (java.io.InputStream is = storageRouter.download(storageType, oldFilePath);
                 java.io.OutputStream os = Files.newOutputStream(archivePath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                is.transferTo(os);
            }

            // 更新旧版本记录的 filePath 指向归档路径
            oldVersion.setFilePath(archivePath.toString());
            fileVersionRepository.save(oldVersion);
            System.out.println("[版本归档] 已归档旧版本文件, fileId=" + existingFile.getId() + ", version=" + oldVersionNum + ", archivePath=" + archivePath);
        } catch (Exception e) {
            System.err.println("[版本归档] 归档旧版本文件失败, fileId=" + existingFile.getId() + ", version=" + oldVersionNum + ", error=" + e.getMessage());
        }
    }
}
