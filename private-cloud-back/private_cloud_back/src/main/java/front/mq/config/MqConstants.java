package front.mq.config;

/**
 * RabbitMQ 队列与交换机常量定义
 */
public final class MqConstants {

    private MqConstants() {}

    // ============ 任务类型 ============
    public static final String TASK_AI_GENERATE = "AI_GENERATE";
    public static final String TASK_FILE_OCR = "FILE_OCR";
    public static final String TASK_FILE_FULLTEXT = "FILE_FULLTEXT";
    public static final String TASK_FILE_OFFICE_CONVERT = "FILE_OFFICE_CONVERT";
    public static final String TASK_FILE_MINDMAP = "FILE_MINDMAP";
    public static final String TASK_SYS_AUDIT_LOG = "SYS_AUDIT_LOG";

    // ============ 交换机 ============
    public static final String EXCHANGE_TASK = "task.exchange";
    public static final String EXCHANGE_TASK_DLX = "task.exchange.dlx"; // 死信交换机

    // ============ 队列 ============
    public static final String QUEUE_AI_GENERATE = "task.ai.generate";
    public static final String QUEUE_FILE_OCR = "task.file.ocr";
    public static final String QUEUE_FILE_FULLTEXT = "task.file.fulltext";
    public static final String QUEUE_FILE_OFFICE_CONVERT = "task.file.office.convert";
    public static final String QUEUE_FILE_MINDMAP = "task.file.mindmap";
    public static final String QUEUE_SYS_AUDIT_LOG = "task.sys.audit.log";

    // ============ 死信队列 ============
    public static final String QUEUE_AI_GENERATE_DLQ = "task.ai.generate.dlq";
    public static final String QUEUE_FILE_OCR_DLQ = "task.file.ocr.dlq";
    public static final String QUEUE_FILE_FULLTEXT_DLQ = "task.file.fulltext.dlq";
    public static final String QUEUE_FILE_OFFICE_CONVERT_DLQ = "task.file.office.convert.dlq";
    public static final String QUEUE_FILE_MINDMAP_DLQ = "task.file.mindmap.dlq";
    public static final String QUEUE_SYS_AUDIT_LOG_DLQ = "task.sys.audit.log.dlq";

    // ============ 路由键 ============
    public static final String ROUTING_KEY_AI_GENERATE = "ai.generate";
    public static final String ROUTING_KEY_FILE_OCR = "file.ocr";
    public static final String ROUTING_KEY_FILE_FULLTEXT = "file.fulltext";
    public static final String ROUTING_KEY_FILE_OFFICE_CONVERT = "file.office.convert";
    public static final String ROUTING_KEY_FILE_MINDMAP = "file.mindmap";
    public static final String ROUTING_KEY_SYS_AUDIT_LOG = "sys.audit.log";
}
