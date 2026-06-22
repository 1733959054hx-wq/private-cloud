package front.storage.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 * 仅在 minio.enabled=true 时生效
 */
@Configuration
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 启动时检查并创建 bucket
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinIO] 自动创建 bucket: {}", bucketName);
            }
            log.info("[MinIO] 客户端初始化成功, endpoint={}, bucket={}", endpoint, bucketName);
        } catch (Exception e) {
            log.warn("[MinIO] 初始化检查失败（服务可能未启动）: {}", e.getMessage());
        }

        return client;
    }
}
