package front.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 队列与交换机配置
 * 仅在 mq.enabled=true 时生效
 */
@Configuration
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true")
public class RabbitMqConfig {

    // ============ 死信交换机 ============
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_TASK_DLX, true, false);
    }

    // ============ 主交换机 ============
    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(MqConstants.EXCHANGE_TASK, true, false);
    }

    // ============ AI 生成队列（长耗时任务，需较长 consumer timeout） ============
    @Bean
    public Queue aiGenerateQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_AI_GENERATE)
                .withArguments(queueArgs("task.ai.generate"))
                .build();
    }

    @Bean
    public Queue aiGenerateDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_AI_GENERATE_DLQ).build();
    }

    @Bean
    public Binding aiGenerateBinding() {
        return BindingBuilder.bind(aiGenerateQueue()).to(taskExchange()).with(MqConstants.ROUTING_KEY_AI_GENERATE);
    }

    @Bean
    public Binding aiGenerateDlqBinding() {
        return BindingBuilder.bind(aiGenerateDlq()).to(dlxExchange()).with(MqConstants.ROUTING_KEY_AI_GENERATE);
    }

    // ============ OCR 队列 ============
    @Bean
    public Queue fileOcrQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_OCR)
                .withArguments(queueArgs("task.file.ocr"))
                .build();
    }

    @Bean
    public Queue fileOcrDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_OCR_DLQ).build();
    }

    @Bean
    public Binding fileOcrBinding() {
        return BindingBuilder.bind(fileOcrQueue()).to(taskExchange()).with(MqConstants.ROUTING_KEY_FILE_OCR);
    }

    @Bean
    public Binding fileOcrDlqBinding() {
        return BindingBuilder.bind(fileOcrDlq()).to(dlxExchange()).with(MqConstants.ROUTING_KEY_FILE_OCR);
    }

    // ============ 全文提取队列 ============
    @Bean
    public Queue fileFulltextQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_FULLTEXT)
                .withArguments(queueArgs("task.file.fulltext"))
                .build();
    }

    @Bean
    public Queue fileFulltextDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_FULLTEXT_DLQ).build();
    }

    @Bean
    public Binding fileFulltextBinding() {
        return BindingBuilder.bind(fileFulltextQueue()).to(taskExchange()).with(MqConstants.ROUTING_KEY_FILE_FULLTEXT);
    }

    @Bean
    public Binding fileFulltextDlqBinding() {
        return BindingBuilder.bind(fileFulltextDlq()).to(dlxExchange()).with(MqConstants.ROUTING_KEY_FILE_FULLTEXT);
    }

    // ============ Office 转换队列 ============
    @Bean
    public Queue fileOfficeConvertQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_OFFICE_CONVERT)
                .withArguments(queueArgs("task.file.office.convert"))
                .build();
    }

    @Bean
    public Queue fileOfficeConvertDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_OFFICE_CONVERT_DLQ).build();
    }

    @Bean
    public Binding fileOfficeConvertBinding() {
        return BindingBuilder.bind(fileOfficeConvertQueue()).to(taskExchange()).with(MqConstants.ROUTING_KEY_FILE_OFFICE_CONVERT);
    }

    @Bean
    public Binding fileOfficeConvertDlqBinding() {
        return BindingBuilder.bind(fileOfficeConvertDlq()).to(dlxExchange()).with(MqConstants.ROUTING_KEY_FILE_OFFICE_CONVERT);
    }

    // ============ 脑图生成队列（AI 长耗时任务） ============
    @Bean
    public Queue fileMindmapQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_MINDMAP)
                .withArguments(queueArgs(MqConstants.ROUTING_KEY_FILE_MINDMAP))
                .build();
    }

    @Bean
    public Queue fileMindmapDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_FILE_MINDMAP_DLQ).build();
    }

    @Bean
    public Binding fileMindmapBinding() {
        return BindingBuilder.bind(fileMindmapQueue()).to(taskExchange()).with(MqConstants.ROUTING_KEY_FILE_MINDMAP);
    }

    @Bean
    public Binding fileMindmapDlqBinding() {
        return BindingBuilder.bind(fileMindmapDlq()).to(dlxExchange()).with(MqConstants.ROUTING_KEY_FILE_MINDMAP);
    }

    // ============ 审计日志队列 ============
    @Bean
    public Queue sysAuditLogQueue() {
        return QueueBuilder.durable(MqConstants.QUEUE_SYS_AUDIT_LOG)
                .withArguments(queueArgs("task.sys.audit.log"))
                .build();
    }

    @Bean
    public Queue sysAuditLogDlq() {
        return QueueBuilder.durable(MqConstants.QUEUE_SYS_AUDIT_LOG_DLQ).build();
    }

    @Bean
    public Binding sysAuditLogBinding() {
        return BindingBuilder.bind(sysAuditLogQueue()).to(taskExchange()).with(MqConstants.ROUTING_KEY_SYS_AUDIT_LOG);
    }

    @Bean
    public Binding sysAuditLogDlqBinding() {
        return BindingBuilder.bind(sysAuditLogDlq()).to(dlxExchange()).with(MqConstants.ROUTING_KEY_SYS_AUDIT_LOG);
    }

    /**
     * 队列公共参数：配置死信路由
     */
    private Map<String, Object> queueArgs(String routingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", MqConstants.EXCHANGE_TASK_DLX);
        args.put("x-dead-letter-routing-key", routingKey);
        return args;
    }
}
