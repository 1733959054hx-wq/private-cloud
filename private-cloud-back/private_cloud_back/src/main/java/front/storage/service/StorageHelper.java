package front.storage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 存储工具服务
 * 提供 MinIO 文件下载到本地临时目录、处理完后回写的辅助方法
 * 用于 OfficeConverter / PDF 预览等依赖本地文件路径的场景
 */
@Service
public class StorageHelper {

    private static final Logger log = LoggerFactory.getLogger(StorageHelper.class);

    @Autowired
    private StorageRouter storageRouter;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 确保文件在本地可访问。
     * - 本地存储：直接返回原路径
     * - MinIO 存储：下载到本地临时目录，返回临时文件路径
     *
     * @param storageType 存储类型
     * @param filePath    文件路径（本地为绝对路径，MinIO 为对象名）
     * @param tempPrefix  临时文件前缀
     * @param tempSuffix  临时文件后缀（如 .docx）
     * @return 本地文件路径
     */
    public Path ensureLocalAccessible(String storageType, String filePath, String tempPrefix, String tempSuffix) throws IOException {
        // 嗅探修复：即使 storageType 标记为 minio，若 filePath 实际是本地路径
        // （含反斜杠、以 . 开头、或为绝对路径），则按本地存储处理。
        // 这能修复历史数据中 storageType 与 filePath 不一致导致的下载失败。
        if (looksLikeLocalPath(filePath)) {
            return Paths.get(filePath);
        }

        if (storageType == null || "local".equalsIgnoreCase(storageType)) {
            return Paths.get(filePath);
        }

        // MinIO 文件：下载到本地临时目录
        Path tempDir = Paths.get(uploadDir, "temp", "conversion");
        Files.createDirectories(tempDir);
        Path tempFile = Files.createTempFile(tempDir, tempPrefix, tempSuffix);
        try (InputStream is = storageRouter.download(storageType, filePath)) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw new IOException("从 MinIO 下载文件失败: " + filePath, e);
        }
        return tempFile;
    }

    /**
     * 判断路径是否为本地文件系统路径。
     * MinIO 对象名使用正斜杠且不含盘符/反斜杠；本地路径常含反斜杠、
     * 以 ./ 或 .\ 开头、或为 Windows 绝对路径（如 D:\...）。
     * 注意：不检查 "departments" 关键词，因为 MinIO 对象名也包含 departments/ 前缀。
     */
    private boolean looksLikeLocalPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        // 含反斜杠 → 本地路径（MinIO 对象名只用正斜杠）
        if (filePath.indexOf('\\') >= 0) return true;
        // 以 ./ 或 .\ 开头 → 本地相对路径
        if (filePath.startsWith("./") || filePath.startsWith(".\\")) return true;
        // Windows 盘符开头 → 本地绝对路径
        if (filePath.length() >= 2 && filePath.charAt(1) == ':') return true;
        // "uploads" 是本地存储目录特征，MinIO 对象名不含此关键词
        if (filePath.contains("uploads")) return true;
        return false;
    }

    /**
     * 清理临时文件
     */
    public void cleanupTempFile(Path tempFile) {
        if (tempFile == null) return;
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("[存储工具] 清理临时文件失败: {}", tempFile, e);
        }
    }

    /**
     * 将本地文件上传回 MinIO（若 storageType 为 minio）
     *
     * @param storageType    目标存储类型
     * @param localFile      本地文件
     * @param targetObjectName MinIO 目标对象名
     * @return 实际存储路径
     */
    public String uploadBackIfNeeded(String storageType, File localFile, String targetObjectName) throws Exception {
        if (storageType == null || "local".equalsIgnoreCase(storageType)) {
            return localFile.getAbsolutePath();
        }
        try (InputStream is = java.nio.file.Files.newInputStream(localFile.toPath())) {
            long size = localFile.length();
            StorageRouter.UploadResult result = storageRouter.upload(is, targetObjectName, localFile.getAbsolutePath(), size, null);
            return result.getPath();
        }
    }
}
