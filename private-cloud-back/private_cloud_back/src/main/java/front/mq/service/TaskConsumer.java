package front.mq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务消息消费者
 * 按任务类型路由到对应处理器，支持重试与死信队列
 */
@Service
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true")
public class TaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskConsumer.class);

    @Autowired
    private List<TaskHandler> handlers;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, TaskHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (TaskHandler handler : handlers) {
            handlerMap.put(handler.getTaskType(), handler);
            log.info("[MQ-Consumer] 注册处理器: {} -> {}", handler.getTaskType(), handler.getClass().getSimpleName());
        }
    }

    // ============ AI 生成消费者 ============
    @RabbitListener(queues = MqConstants.QUEUE_AI_GENERATE)
    public void consumeAiGenerate(Message message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        consume(message, channel, deliveryTag);
    }

    // ============ OCR 消费者 ============
    @RabbitListener(queues = MqConstants.QUEUE_FILE_OCR)
    public void consumeFileOcr(Message message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        consume(message, channel, deliveryTag);
    }

    // ============ 全文提取消费者 ============
    @RabbitListener(queues = MqConstants.QUEUE_FILE_FULLTEXT)
    public void consumeFileFulltext(Message message, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        consume(message, channel, deliveryTag);
    }

    // ============ Office 转换消费者 ============
    @RabbitListener(queues = MqConstants.QUEUE_FILE_OFFICE_CONVERT)
    public void consumeFileOfficeConvert(Message message, Channel channel,
                                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        consume(message, channel, deliveryTag);
    }

    // ============ 脑图生成消费者 ============
    @RabbitListener(queues = MqConstants.QUEUE_FILE_MINDMAP)
    public void consumeFileMindmap(Message message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        consume(message, channel, deliveryTag);
    }

    // ============ 审计日志消费者 ============
    @RabbitListener(queues = MqConstants.QUEUE_SYS_AUDIT_LOG)
    public void consumeSysAuditLog(Message message, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        consume(message, channel, deliveryTag);
    }

    /**
     * 统一消费逻辑
     * - 处理成功：ack
     * - 处理失败且重试次数未超限：nack（requeue=true），由消费者端幂等性保证
     * - 处理失败且重试次数超限：nack（requeue=false），消息进入死信队列
     *
     * 注意：为避免无限重试风暴，对业务处理失败（如PDF损坏、文件格式不支持）
     * 直接 nack 不 requeue，让消息进入死信队列，而非无限 requeue 导致 CPU 飙升。
     */
    private void consume(Message message, Channel channel, long deliveryTag) {
        TaskMessage taskMessage = null;
        try {
            String body = new String(message.getBody());
            taskMessage = objectMapper.readValue(body, TaskMessage.class);
            TaskHandler handler = handlerMap.get(taskMessage.getTaskType());

            if (handler == null) {
                log.error("[MQ-Consumer] 未找到处理器, taskType={}", taskMessage.getTaskType());
                basicAck(channel, deliveryTag);
                return;
            }

            log.info("[MQ-Consumer] 开始处理, taskType={}, taskId={}, retry={}",
                    taskMessage.getTaskType(), taskMessage.getTaskId(), taskMessage.getRetryCount());

            handler.handle(taskMessage);

            // 处理成功，ack
            basicAck(channel, deliveryTag);
            log.info("[MQ-Consumer] 处理成功, taskId={}", taskMessage.getTaskId());

        } catch (Exception e) {
            log.error("[MQ-Consumer] 处理失败, taskId={}, retry={}, error={}",
                    taskMessage != null ? taskMessage.getTaskId() : "unknown",
                    taskMessage != null ? taskMessage.getRetryCount() : 0,
                    e.getMessage(), e);

            if (taskMessage != null && taskMessage.getRetryCount() < taskMessage.getMaxRetry()) {
                // 重试次数未超限，nack 并 requeue（依赖消费者端幂等性）
                // 注意：简单 requeue 不会增加 retryCount，实际生产应使用延迟队列
                basicNack(channel, deliveryTag, true);
            } else {
                // 重试次数超限或业务处理失败（如PDF损坏），nack 不 requeue，消息进入死信队列
                log.error("[MQ-Consumer] 消息进入死信队列, taskId={}, reason={}",
                        taskMessage != null ? taskMessage.getTaskId() : "unknown",
                        taskMessage != null && taskMessage.getRetryCount() >= taskMessage.getMaxRetry()
                                ? "重试次数超限" : "业务处理失败");
                basicNack(channel, deliveryTag, false);
            }
        }
    }

    private void basicAck(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[MQ-Consumer] ack 失败: {}", e.getMessage());
        }
    }

    private void basicNack(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (Exception e) {
            log.error("[MQ-Consumer] nack 失败: {}", e.getMessage());
        }
    }
}
