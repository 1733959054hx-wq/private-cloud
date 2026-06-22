package front.mq.handler;

import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import front.mq.service.TaskHandler;
import front.system.entity.SysOperationLog;
import front.system.repository.SysOperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志任务处理器
 * 将审计日志写入数据库，不抛异常（日志失败不应影响业务）
 */
@Component
public class SysAuditLogHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SysAuditLogHandler.class);

    @Autowired
    private SysOperationLogRepository operationLogRepository;

    @Override
    @Transactional
    public void handle(TaskMessage message) throws Exception {
        Map<String, Object> payload = message.getPayload();
        try {
            SysOperationLog logEntry = new SysOperationLog();
            logEntry.setModule((String) payload.get("module"));
            logEntry.setOperation((String) payload.get("operation"));
            logEntry.setTargetType((String) payload.get("targetType"));
            Object targetId = payload.get("targetId");
            logEntry.setTargetId(targetId != null ? Long.valueOf(targetId.toString()) : null);
            String detail = (String) payload.get("detail");
            logEntry.setDetail(detail != null && detail.length() > 2000 ? detail.substring(0, 2000) : detail);
            logEntry.setStatus((String) payload.get("status"));
            Object userId = payload.get("userId");
            logEntry.setUserId(userId != null ? Long.valueOf(userId.toString()) : null);
            logEntry.setUsername((String) payload.get("username"));
            logEntry.setIp((String) payload.get("ip"));
            logEntry.setUserAgent((String) payload.get("userAgent"));
            logEntry.setCreateTime(LocalDateTime.now());
            operationLogRepository.save(logEntry);
            log.debug("[MQ-Audit] 审计日志已记录, module={}, operation={}", payload.get("module"), payload.get("operation"));
        } catch (Exception e) {
            log.error("[MQ-Audit] 审计日志记录失败: {}", e.getMessage(), e);
            // 不抛异常，避免重试
        }
    }

    @Override
    public String getTaskType() {
        return MqConstants.TASK_SYS_AUDIT_LOG;
    }
}
