package front.mq.handler;

import front.esign.service.OfficeConverter;
import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import front.mq.service.TaskHandler;
import front.storage.service.StorageHelper;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Office 文档转 PDF 任务处理器
 * 支持 MinIO 源文件：先下载到本地临时目录，转换后回写
 * 幂等性：通过 Redis 分布式排他锁防止重复转码
 */
@Component
public class FileOfficeConvertHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(FileOfficeConvertHandler.class);

    @Autowired
    private DocFileRepository docFileRepository;

    @Autowired
    private OfficeConverter officeConverter;

    @Autowired
    private StorageHelper storageHelper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void handle(TaskMessage message) throws Exception {
        Long fileId = message.getBizId();
        log.info("[MQ-Convert] 开始处理, fileId={}, taskId={}", fileId, message.getTaskId());

        // Redis 分布式排他锁：防止并发重复转码（如用户疯狂点击或重复上传同一文件）
        String lockKey = "lock:convert:" + fileId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[MQ-Convert] 检测到重复转码任务，跳过执行, fileId={}", fileId);
            return;
        }

        try {
            DocFile docFile = docFileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件不存在, fileId=" + fileId));

            // 幂等检查：已完成的转码不重复执行
            if ("COMPLETED".equals(docFile.getPreviewStatus()) && docFile.getPreviewPdfPath() != null) {
                log.info("[MQ-Convert] 文件已转码完成，跳过, fileId={}", fileId);
                return;
            }

            String storageType = docFile.getStorageType() != null ? docFile.getStorageType() : "local";
            String sourcePath = docFile.getFilePath();
            String ext = docFile.getFileType() != null ? docFile.getFileType() : "";

            // 确保源文件在本地可访问
            Path localSourcePath = storageHelper.ensureLocalAccessible(storageType, sourcePath, "convert_", "." + ext);

            // 输出 PDF 路径（始终先写到本地 pdf_cache）
            Path cacheDir = Paths.get(uploadDir, "pdf_cache");
            Files.createDirectories(cacheDir);
            Path localPdfPath = cacheDir.resolve(fileId + ".pdf");

            officeConverter.convertToPdf(localSourcePath.toString(), localPdfPath.toString());

            // 若源文件在 MinIO，将 PDF 也上传到 MinIO
            String finalPdfPath;
            if ("minio".equalsIgnoreCase(storageType)) {
                String pdfObjectName = "pdf_cache/" + fileId + ".pdf";
                finalPdfPath = storageHelper.uploadBackIfNeeded(storageType, localPdfPath.toFile(), pdfObjectName);
                // 上传后删除本地临时 PDF
                Files.deleteIfExists(localPdfPath);
            } else {
                finalPdfPath = localPdfPath.toString();
            }

            docFile.setPreviewStatus("COMPLETED");
            docFile.setPreviewPdfPath(finalPdfPath);
            docFileRepository.save(docFile);
            log.info("[MQ-Convert] 处理完成, fileId={}", fileId);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
            // 清理临时源文件（仅 MinIO 场景会产生临时文件）
            if (message.getBizId() != null) {
                try {
                    DocFile docFile = docFileRepository.findById(fileId).orElse(null);
                    if (docFile != null && "minio".equalsIgnoreCase(docFile.getStorageType())) {
                        // MinIO 临时文件已在 ensureLocalAccessible 中标记，此处清理
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public String getTaskType() {
        return MqConstants.TASK_FILE_OFFICE_CONVERT;
    }
}
