package front.mq.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统一任务消息格式
 * 用于在 RabbitMQ 中传递异步任务
 */
public class TaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID（UUID） */
    private String taskId;

    /** 任务类型：AI_GENERATE / FILE_OCR / FILE_FULLTEXT / FILE_OFFICE_CONVERT / SYS_AUDIT_LOG */
    private String taskType;

    /** 业务 ID（如 fileId、docId） */
    private Long bizId;

    /** 任务参数（JSON 序列化后传输） */
    private Map<String, Object> payload;

    /** 已重试次数 */
    private int retryCount;

    /** 最大重试次数 */
    private int maxRetry = 3;

    /** 创建时间 */
    private LocalDateTime createTime;

    public TaskMessage() {
    }

    public TaskMessage(String taskId, String taskType, Long bizId, Map<String, Object> payload) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.bizId = bizId;
        this.payload = payload;
        this.createTime = LocalDateTime.now();
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getMaxRetry() { return maxRetry; }
    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return "TaskMessage{taskId='" + taskId + "', taskType='" + taskType +
                "', bizId=" + bizId + ", retryCount=" + retryCount + "}";
    }
}
