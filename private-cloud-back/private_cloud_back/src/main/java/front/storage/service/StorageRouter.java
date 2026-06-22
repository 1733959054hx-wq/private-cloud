package front.storage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;

/**
 * 存储路由服务
 * 根据 storage_type 决定使用本地存储还是 MinIO 存储
 * 同时负责新文件存储位置决策与降级逻辑
 */
@Service
public class StorageRouter {

    private static final Logger log = LoggerFactory.getLogger(StorageRouter.class);

    @Autowired
    @Qualifier("localStorageService")
    private StorageService localStorageService;

    @Autowired(required = false)
    @Qualifier("minioStorageService")
    private StorageService minioStorageService;

    @Value("${minio.enabled:false}")
    private boolean minioEnabled;

    @Value("${minio.min-size:0}")
    private long minioMinSize;

    /**
     * 根据 storageType 获取对应的存储服务
     */
    public StorageService getStorage(String storageType) {
        if ("minio".equalsIgnoreCase(storageType) && minioStorageService != null) {
            return minioStorageService;
        }
        return localStorageService;
    }

    /**
     * 解析实际的存储类型：若 path 实际是本地路径（含反斜杠/盘符/uploads 等），
     * 即使 storageType 标记为 minio，也返回 local。
     * 用于修复历史数据中 storageType 与 filePath 不一致的问题。
     */
    public String resolveStorageType(String storageType, String path) {
        if (storageType == null || "local".equalsIgnoreCase(storageType)) {
            return "local";
        }
        if (path != null && !path.isEmpty() && looksLikeLocalPath(path)) {
            return "local";
        }
        return storageType;
    }

    /**
     * 判断路径是否为本地文件系统路径。
     * MinIO 对象名使用正斜杠且不含盘符/反斜杠；本地路径常含反斜杠、
     * 以 ./ 或 .\ 开头、或为 Windows 绝对路径（如 D:\...）。
     * 注意：不检查 "departments" 关键词，因为 MinIO 对象名也包含 departments/ 前缀。
     */
    private boolean looksLikeLocalPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        if (filePath.indexOf('\\') >= 0) return true;
        if (filePath.startsWith("./") || filePath.startsWith(".\\")) return true;
        if (filePath.length() >= 2 && filePath.charAt(1) == ':') return true;
        // "uploads" 是本地存储目录特征，MinIO 对象名不含此关键词
        if (filePath.contains("uploads")) return true;
        return false;
    }

    /**
     * 判断新文件是否应该走 MinIO
     * @param fileSize 文件大小（字节）
     * @return true 表示走 MinIO
     */
    public boolean shouldUseMinio(long fileSize) {
        if (!minioEnabled || minioStorageService == null) {
            return false;
        }
        // minSize=0 表示全部走 MinIO
        return minioMinSize <= 0 || fileSize >= minioMinSize;
    }

    /**
     * 上传文件，自动选择存储方式
     *
     * @param inputStream  文件流
     * @param objectName   对象名（本地为绝对路径，MinIO 为对象 key）
     * @param localPath    本地存储时的绝对路径
     * @param size         文件大小
     * @param contentType  MIME 类型
     * @return UploadResult 包含实际存储类型与路径
     */
    public UploadResult upload(InputStream inputStream, String objectName, String localPath,
                                long size, String contentType) {
        // 决定是否走 MinIO
        if (shouldUseMinio(size)) {
            try {
                log.info("[存储路由] 使用 MinIO 扩展存储: objectName={}, size={}", objectName, size);
                String path = minioStorageService.upload(inputStream, objectName, size, contentType);
                return new UploadResult("minio", path);
            } catch (Exception e) {
                log.warn("[存储路由] MinIO 上传失败，降级到本地存储: objectName={}, error={}", objectName, e.getMessage());
                // 降级到本地：需要重新获取流（原流可能已消费），由调用方负责
                throw new StorageFallbackException("MinIO 上传失败", e);
            }
        }
        // 走本地存储
        try {
            log.info("[存储路由] 使用旧版本地存储: localPath={}, size={}", localPath, size);
            String path = localStorageService.upload(inputStream, localPath, size, contentType);
            return new UploadResult("local", path);
        } catch (Exception e) {
            throw new RuntimeException("本地存储上传失败: " + localPath, e);
        }
    }

    /**
     * 下载文件
     */
    public InputStream download(String storageType, String path) throws Exception {
        storageType = resolveStorageType(storageType, path);
        log.debug("[存储路由] 下载文件: storageType={}, path={}", storageType, path);
        return getStorage(storageType).download(path);
    }

    /**
     * 删除文件
     */
    public void delete(String storageType, String path) {
        storageType = resolveStorageType(storageType, path);
        log.info("[存储路由] 删除文件: storageType={}, path={}", storageType, path);
        try {
            getStorage(storageType).delete(path);
        } catch (Exception e) {
            log.warn("[存储路由] 删除文件失败: storageType={}, path={}, error={}", storageType, path, e.getMessage());
        }
    }

    /**
     * 判断文件是否存在
     */
    public boolean exists(String storageType, String path) {
        storageType = resolveStorageType(storageType, path);
        boolean exists = getStorage(storageType).exists(path);
        log.debug("[存储路由] 文件是否存在: storageType={}, path={}, exists={}", storageType, path, exists);
        return exists;
    }

    /**
     * 获取预签名 URL（仅 MinIO 支持）
     */
    public String getPresignedUrl(String storageType, String path, Duration expiry) {
        storageType = resolveStorageType(storageType, path);
        StorageService storage = getStorage(storageType);
        return storage.getPresignedUrl(path, expiry);
    }

    /**
     * 复制文件（同存储类型内部复制）
     */
    public void copy(String storageType, String sourcePath, String targetPath) throws Exception {
        storageType = resolveStorageType(storageType, sourcePath);
        getStorage(storageType).copy(sourcePath, targetPath);
    }

    /**
     * MinIO 不可用时的降级异常
     * 调用方捕获后应使用本地存储重试
     */
    public static class StorageFallbackException extends RuntimeException {
        public StorageFallbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 上传结果
     */
    public static class UploadResult {
        private final String storageType;
        private final String path;

        public UploadResult(String storageType, String path) {
            this.storageType = storageType;
            this.path = path;
        }

        public String getStorageType() { return storageType; }
        public String getPath() { return path; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UploadResult)) return false;
            UploadResult that = (UploadResult) o;
            return Objects.equals(storageType, that.storageType) && Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storageType, path);
        }
    }
}
