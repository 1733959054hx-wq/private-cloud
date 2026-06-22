package front.storage.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储实现
 * 仅在 minio.enabled=true 时注册
 */
@Service("minioStorageService")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class MinioStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.presigned-expiry:30}")
    private int presignedExpiryMinutes;

    @Override
    public String upload(InputStream inputStream, String objectName, long size, String contentType) throws Exception {
        PutObjectArgs.Builder builder = PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .contentType(contentType != null ? contentType : "application/octet-stream");
        if (size > 0) {
            builder.stream(inputStream, size, -1);
        } else {
            // 未知大小时使用分片上传，partSize=10MB
            builder.stream(inputStream, -1, 10 * 1024 * 1024L);
        }
        minioClient.putObject(builder.build());
        return objectName;
    }

    @Override
    public InputStream download(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    @Override
    public void delete(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    @Override
    public boolean exists(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            // NoSuchKey 表示对象不存在
            return false;
        } catch (Exception e) {
            log.warn("[MinIO] 检查对象存在性失败: objectName={}, error={}", objectName, e.getMessage());
            return false;
        }
    }

    @Override
    public String getPresignedUrl(String objectName, Duration expiry) {
        try {
            int expirySeconds = (int) expiry.getSeconds();
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.warn("[MinIO] 生成预签名 URL 失败: objectName={}, error={}", objectName, e.getMessage());
            return null;
        }
    }

    @Override
    public void copy(String sourceObjectName, String targetObjectName) throws Exception {
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(bucketName)
                .object(targetObjectName)
                .source(CopySource.builder()
                        .bucket(bucketName)
                        .object(sourceObjectName)
                        .build())
                .build());
    }

    @Override
    public String getStorageType() {
        return "minio";
    }

    /**
     * 获取默认的预签名 URL（使用配置的过期时间）
     */
    public String getPresignedUrlWithDefaultExpiry(String objectName) {
        return getPresignedUrl(objectName, Duration.ofMinutes(presignedExpiryMinutes));
    }
}
