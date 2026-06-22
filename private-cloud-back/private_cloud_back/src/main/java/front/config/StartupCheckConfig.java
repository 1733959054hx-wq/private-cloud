package front.config;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.net.Socket;
/**
 * 启动验证器
 *
 * 在应用启动完成后（ApplicationReadyEvent），根据 app.startup-check.enabled 开关
 * 验证外部服务（Redis、MinIO、RabbitMQ、Elasticsearch）是否已启动。
 *
 * - true：验证失败则抛异常并阻止启动（实际是日志 ERROR 提醒，因 ApplicationReadyEvent 已晚于启动）
 * - false：跳过验证，允许启动
 *
 * 注：因 ApplicationReadyEvent 在应用启动完成后触发，无法真正阻止启动，
 *     但会以 ERROR 日志显著提醒，便于开发时发现服务未启动。
 *     若需真正阻止启动，可改用 ApplicationEnvironmentPreparedEvent。
 */
@Configuration
public class StartupCheckConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupCheckConfig.class);

    @Value("${app.startup-check.enabled:true}")
    private boolean startupCheckEnabled;

    @Value("${minio.enabled:true}")
    private boolean minioEnabled;

    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${mq.enabled:true}")
    private boolean mqEnabled;

    @Value("${spring.rabbitmq.host:}")
    private String rabbitmqHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitmqPort;

    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.elasticsearch.enabled:true}")
    private boolean esEnabled;

    @Value("${spring.elasticsearch.uris:}")
    private String esUris;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!startupCheckEnabled) {
            log.warn("[启动验证] 已关闭（app.startup-check.enabled=false），跳过外部服务验证");
            return;
        }

        log.info("[启动验证] 开始验证外部服务依赖...");
        int failures = 0;

        // 1. Redis
        if (!checkRedis()) failures++;

        // 2. MinIO
        if (minioEnabled && !checkMinio()) failures++;

        // 3. RabbitMQ
        if (mqEnabled && !checkRabbitMQ()) failures++;

        // 4. Elasticsearch
        if (esEnabled && !checkElasticsearch()) failures++;

        if (failures > 0) {
            log.error("[启动验证] 共 {} 个外部服务验证失败，请检查服务是否已启动！", failures);
            // 不抛异常，因为应用已启动完成；通过 ERROR 日志提醒
        } else {
            log.info("[启动验证] 所有外部服务验证通过 ✓");
        }

        // ============ 三大扩展运行模式提示 ============
        log.info("[扩展模式] MinIO 对象存储: {}", minioEnabled ? "已启用（新文件走 MinIO）" : "已关闭（走本地磁盘存储）");
        log.info("[扩展模式] RabbitMQ 消息队列: {}", mqEnabled ? "已启用（异步任务走 MQ）" : "已关闭（降级为线程池执行）");
        log.info("[扩展模式] Caffeine 二级缓存: 已启用（L1 Caffeine + L2 Redis）");
    }

    private boolean checkRedis() {
        try {
            if (redisConnectionFactory == null) {
                log.warn("[启动验证] Redis 连接工厂未配置，跳过");
                return true;
            }
            String ping = redisConnectionFactory.getConnection().ping();
            log.info("[启动验证] Redis 连接正常: {}:{} ping={}", redisHost, redisPort, ping);
            return true;
        } catch (Exception e) {
            log.error("[启动验证] Redis 连接失败: {}:{} error={}", redisHost, redisPort, e.getMessage());
            return false;
        }
    }

    private boolean checkMinio() {
        try {
            if (minioClient == null) {
                log.warn("[启动验证] MinIO 客户端未配置，跳过");
                return true;
            }
            // 简单调用 listBuckets 验证连接
            minioClient.listBuckets();
            log.info("[启动验证] MinIO 连接正常: {}", minioEndpoint);
            return true;
        } catch (Exception e) {
            log.error("[启动验证] MinIO 连接失败: {} error={}", minioEndpoint, e.getMessage());
            return false;
        }
    }

    private boolean checkRabbitMQ() {
        Connection connection = null;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rabbitmqHost);
            factory.setPort(rabbitmqPort);
            // 使用默认 guest/guest，实际应从配置读取
            factory.setConnectionTimeout(5000);
            connection = factory.newConnection("startup-check");
            log.info("[启动验证] RabbitMQ 连接正常: {}:{}", rabbitmqHost, rabbitmqPort);
            return true;
        } catch (Exception e) {
            log.error("[启动验证] RabbitMQ 连接失败: {}:{} error={}", rabbitmqHost, rabbitmqPort, e.getMessage());
            return false;
        } finally {
            if (connection != null && connection.isOpen()) {
                try { connection.close(); } catch (Exception ignored) {}
            }
        }
    }

    private boolean checkElasticsearch() {
        if (esUris == null || esUris.isEmpty()) {
            log.warn("[启动验证] Elasticsearch URIs 未配置，跳过");
            return true;
        }
        // 简单 TCP 端口检测
        String uri = esUris.split(",")[0].trim();
        try {
            // 解析 http://host:port
            String hostPort = uri.replace("http://", "").replace("https://", "");
            String[] parts = hostPort.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
            try (Socket socket = new Socket(host, port)) {
                socket.setSoTimeout(3000);
                log.info("[启动验证] Elasticsearch 端口可达: {}", uri);
                return true;
            }
        } catch (Exception e) {
            log.error("[启动验证] Elasticsearch 连接失败: {} error={}", uri, e.getMessage());
            return false;
        }
    }
}
