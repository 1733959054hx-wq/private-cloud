package front.intelligence.preview.service;

import front.storage.service.StorageHelper;
import front.storage.service.StorageRouter;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class PreviewService {

    private static final Set<String> PREVIEWABLE_TYPES = Set.of(
            "pdf", "docx", "doc", "pptx", "xlsx", "xls",
            "mp4", "mp3", "wav", "avi", "mov", "mkv", "flv", "webm", "ogg",
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "tiff", "tif",
            "txt", "md", "csv", "log", "json", "xml", "html", "htm", "css", "js",
            "java", "py", "c", "cpp", "h", "go", "rs", "ts", "sql", "yaml", "yml",
            "ini", "conf", "cfg", "sh", "bat", "properties"
    );

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private StorageRouter storageRouter;

    @Autowired
    private StorageHelper storageHelper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public Resource getPreviewResource(Long fileId) {
        DocFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        String ext = file.getFileType();
        if (ext != null && !PREVIEWABLE_TYPES.contains(ext.toLowerCase())) {
            throw new RuntimeException("该文件类型不支持在线预览: " + ext);
        }

        String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
        String filePath = file.getFilePath();

        // MinIO 存储：下载到字节数组返回
        if ("minio".equalsIgnoreCase(storageType)) {
            try (InputStream is = storageRouter.download(storageType, filePath)) {
                return new ByteArrayResource(is.readAllBytes());
            } catch (Exception e) {
                // 降级：MinIO 不可用或文件不存在时，尝试从本地 uploads 目录查找
                File localFile = resolveLocalFile(filePath);
                if (localFile != null && localFile.exists()) {
                    return new FileSystemResource(localFile);
                }
                throw new RuntimeException("从 MinIO 读取文件失败: " + e.getMessage(), e);
            }
        }

        // 本地存储：保持原有逻辑
        File fileObj = new File(filePath);
        if (!fileObj.exists()) {
            // 降级：尝试从 uploads 目录查找（兼容历史数据中 filePath 为相对路径的情况）
            File localFile = resolveLocalFile(filePath);
            if (localFile != null && localFile.exists()) {
                return new FileSystemResource(localFile);
            }
            throw new RuntimeException("文件不存在于存储路径");
        }
        return new FileSystemResource(fileObj);
    }

    /**
     * 尝试从本地 uploads 目录解析文件路径。
     * 兼容 filePath 为 MinIO 对象名（如 departments/1/files/xxx.png）的情况，
     * 尝试在 uploads 目录下查找对应文件。
     * @return 本地文件对象；不存在则返回 null
     */
    private File resolveLocalFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        // 尝试 uploads/<filePath>
        try {
            Path candidate = Paths.get(uploadDir, filePath).toAbsolutePath().normalize();
            if (candidate.toFile().exists()) return candidate.toFile();
        } catch (Exception ignored) { }
        return null;
    }

    public String getFileType(Long fileId) {
        DocFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
        return file.getFileType();
    }

    public DocFile getFileInfo(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));
    }

    /**
     * 更新文件预览状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePreviewStatus(Long fileId, String status) {
        DocFile file = getFileInfo(fileId);
        file.setPreviewStatus(status);
        fileRepository.save(file);
    }

    /**
     * 转换成功后，持久化缓存路径并更新状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePreviewSuccess(Long fileId, String pdfPath) {
        DocFile file = getFileInfo(fileId);
        file.setPreviewStatus("COMPLETED");
        file.setPreviewPdfPath(pdfPath);
        fileRepository.save(file);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFilePath(Long fileId, String newFilePath) {
        DocFile file = getFileInfo(fileId);
        file.setFilePath(newFilePath);
        fileRepository.save(file);
    }
}
