package front.mq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 任务消息发布者
 * 负责将异步任务发送到 RabbitMQ；MQ 不可用时降级为线程池执行
 */
@Service
public class TaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskPublisher.class);

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mq.enabled:false}")
    private boolean mqEnabled;

    /**
     * 发送任务消息
     *
     * @param taskType 任务类型（见 MqConstants.TASK_*）
     * @param bizId    业务 ID
     * @param payload  任务参数
     * @return 任务 ID；若 MQ 不可用返回 null（调用方应走降级逻辑）
     */
    public String publish(String taskType, Long bizId, Map<String, Object> payload) {
        String taskId = UUID.randomUUID().toString();
        TaskMessage message = new TaskMessage(taskId, taskType, bizId, payload);

        if (!mqEnabled || rabbitTemplate == null) {
            log.info("[MQ] 使用旧版线程池执行: taskId={}, taskType={}, bizId={} (mqEnabled={})", taskId, taskType, bizId, mqEnabled);
            return null;
        }

        try {
            String routingKey = resolveRoutingKey(taskType);
            String json = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_TASK, routingKey, json);
            log.info("[MQ] 使用 RabbitMQ 扩展队列: taskId={}, taskType={}, bizId={}", taskId, taskType, bizId);
            return taskId;
        } catch (AmqpException | JsonProcessingException e) {
            log.warn("[MQ] RabbitMQ 发送失败，降级为旧版线程池执行: taskType={}, bizId={}, error={}", taskType, bizId, e.getMessage());
            return null;
        }
    }

    /**
     * 根据任务类型解析路由键
     */
    private String resolveRoutingKey(String taskType) {
        return switch (taskType) {
            case MqConstants.TASK_AI_GENERATE -> MqConstants.ROUTING_KEY_AI_GENERATE;
            case MqConstants.TASK_FILE_OCR -> MqConstants.ROUTING_KEY_FILE_OCR;
            case MqConstants.TASK_FILE_FULLTEXT -> MqConstants.ROUTING_KEY_FILE_FULLTEXT;
            case MqConstants.TASK_FILE_OFFICE_CONVERT -> MqConstants.ROUTING_KEY_FILE_OFFICE_CONVERT;
            case MqConstants.TASK_FILE_MINDMAP -> MqConstants.ROUTING_KEY_FILE_MINDMAP;
            case MqConstants.TASK_SYS_AUDIT_LOG -> MqConstants.ROUTING_KEY_SYS_AUDIT_LOG;
            default -> throw new IllegalArgumentException("未知任务类型: " + taskType);
        };
    }

    /**
     * 判断 MQ 是否可用
     */
    public boolean isMqAvailable() {
        return mqEnabled && rabbitTemplate != null;
    }
}
