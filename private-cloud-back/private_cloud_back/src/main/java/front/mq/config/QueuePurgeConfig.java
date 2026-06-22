package front.mq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.AmqpAdmin;

import jakarta.annotation.PostConstruct;

/**
 * RabbitMQ 队列清理器
 * 通过配置 mq.purge-on-startup=true 在启动时清空所有任务队列的积压消息
 * 用于解决：后端重启后自动消费历史积压消息（如未登录就执行文本提取）
 *
 * 使用方法：
 * 1. 在 application.yml 中设置 mq.purge-on-startup: true
 * 2. 重启后端，积压消息会被清空
 * 3. 清空后可改回 false（或删除该配置），避免每次启动都清空
 */
@Configuration
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true")
public class QueuePurgeConfig {

    private static final Logger log = LoggerFactory.getLogger(QueuePurgeConfig.class);

    @Autowired(required = false)
    private AmqpAdmin amqpAdmin;

    @Value("${mq.purge-on-startup:false}")
    private boolean purgeOnStartup;

    @PostConstruct
    public void purgeQueuesOnStartup() {
        if (!purgeOnStartup || amqpAdmin == null) {
            return;
        }

        String[] queuesToPurge = {
            MqConstants.QUEUE_FILE_FULLTEXT,
            MqConstants.QUEUE_FILE_OCR,
            MqConstants.QUEUE_FILE_MINDMAP,
            MqConstants.QUEUE_FILE_OFFICE_CONVERT,
            MqConstants.QUEUE_AI_GENERATE,
            // 不清空审计日志队列，避免丢失重要日志
        };

        log.info("[MQ] 开始清空队列积压消息 (mq.purge-on-startup=true)...");
        for (String queueName : queuesToPurge) {
            try {
                amqpAdmin.purgeQueue(queueName, false);
                log.info("[MQ] 已清空队列: {}", queueName);
            } catch (Exception e) {
                log.warn("[MQ] 清空队列失败: {}, error: {}", queueName, e.getMessage());
            }
        }
        log.info("[MQ] 队列清空完成。建议将 mq.purge-on-startup 改回 false 避免每次启动都清空");
    }
}
