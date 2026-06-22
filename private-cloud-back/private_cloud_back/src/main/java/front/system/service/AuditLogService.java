package front.system.service;

import front.mq.config.MqConstants;
import front.mq.service.TaskPublisher;
import front.system.entity.SysOperationLog;
import front.system.repository.SysOperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全审计日志服务
 *
 * 记录所有敏感操作，包括：
 * - 认证操作：登录成功/失败、登出
 * - 用户管理：创建/修改/删除用户、密码变更、角色变更
 * - 文件操作：预览/下载/删除/移动文件
 * - 系统操作：权限变更、系统配置修改
 *
 * 异步写入策略：优先走 RabbitMQ，MQ 不可用时降级为 @Async 线程池
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    /** 操作模块常量 */
    public static final String MODULE_AUTH = "AUTH";
    public static final String MODULE_USER = "USER";
    public static final String MODULE_FILE = "FILE";
    public static final String MODULE_ROLE = "ROLE";
    public static final String MODULE_SYSTEM = "SYSTEM";

    /** 操作类型常量 */
    public static final String OP_LOGIN = "LOGIN";
    public static final String OP_LOGOUT = "LOGOUT";
    public static final String OP_CREATE = "CREATE";
    public static final String OP_UPDATE = "UPDATE";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_VIEW = "VIEW";
    public static final String OP_DOWNLOAD = "DOWNLOAD";
    public static final String OP_PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String OP_ROLE_CHANGE = "ROLE_CHANGE";
    public static final String OP_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String OP_TRANSFER = "TRANSFER";
    public static final String OP_RENAME = "RENAME";
    public static final String OP_MOVE = "MOVE";
    public static final String OP_COPY = "COPY";
    public static final String OP_RESTORE = "RESTORE";

    /** 操作结果常量 */
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAIL = "fail";

    @Autowired
    private SysOperationLogRepository operationLogRepository;

    @Autowired
    private TaskPublisher taskPublisher;

    /**
     * 异步记录审计日志（不阻塞业务线程）
     * 优先走 MQ，MQ 不可用时降级为 @Async 线程池
     */
    public void logAsync(String module, String operation, String targetType,
                         Long targetId, String detail, String status,
                         Long userId, String username) {
        // 提取请求信息（必须在当前线程完成，否则 RequestContextHolder 已被清空）
        String ip = "system";
        String userAgent = null;
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ipHeader = request.getHeader("X-Forwarded-For");
                ip = (ipHeader == null || ipHeader.isEmpty()) ? request.getRemoteAddr() : ipHeader;
                userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 500) {
                    userAgent = userAgent.substring(0, 500);
                }
            }
        } catch (Exception e) {
            ip = "unknown";
        }

        // 构造 MQ payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("module", module);
        payload.put("operation", operation);
        payload.put("targetType", targetType);
        payload.put("targetId", targetId);
        payload.put("detail", truncateDetail(detail));
        payload.put("status", status);
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("ip", ip);
        payload.put("userAgent", userAgent);

        // 优先走 MQ
        String taskId = taskPublisher.publish(MqConstants.TASK_SYS_AUDIT_LOG, targetId, payload);
        if (taskId != null) {
            return;
        }

        // MQ 不可用，降级为 @Async 线程池
        logSyncFallback(payload);
    }

    /**
     * 降级路径：通过 @Async 线程池写入
     */
    @Async("ocrExecutor")
    @Transactional
    public void logSyncFallback(Map<String, Object> payload) {
        try {
            SysOperationLog logEntry = new SysOperationLog();
            logEntry.setModule((String) payload.get("module"));
            logEntry.setOperation((String) payload.get("operation"));
            logEntry.setTargetType((String) payload.get("targetType"));
            Object targetId = payload.get("targetId");
            logEntry.setTargetId(targetId != null ? Long.valueOf(targetId.toString()) : null);
            logEntry.setDetail((String) payload.get("detail"));
            logEntry.setStatus((String) payload.get("status"));
            Object userId = payload.get("userId");
            logEntry.setUserId(userId != null ? Long.valueOf(userId.toString()) : null);
            logEntry.setUsername((String) payload.get("username"));
            logEntry.setIp((String) payload.get("ip"));
            logEntry.setUserAgent((String) payload.get("userAgent"));
            logEntry.setCreateTime(LocalDateTime.now());
            operationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("[审计日志] 降级写入失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步记录审计日志（用于关键操作必须确保日志写入的场景）
     */
    @Transactional
    public void log(String module, String operation, String targetType,
                    Long targetId, String detail, String status,
                    Long userId, String username) {
        try {
            SysOperationLog logEntry = new SysOperationLog();
            logEntry.setModule(module);
            logEntry.setOperation(operation);
            logEntry.setTargetType(targetType);
            logEntry.setTargetId(targetId);
            logEntry.setDetail(truncateDetail(detail));
            logEntry.setStatus(status);
            logEntry.setUserId(userId);
            logEntry.setUsername(username);
            fillRequestInfo(logEntry);
            operationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("[审计日志] 记录失败: module={}, operation={}, detail={}", module, operation, detail, e);
        }
    }

    /**
     * 分页查询审计日志
     */
    public Page<SysOperationLog> searchAuditLogs(String module, String operation,
                                                  Long userId, String status,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  int page, int size) {
        return operationLogRepository.searchAuditLogs(
                module, operation, userId, status, startTime, endTime,
                PageRequest.of(page, size));
    }

    /**
     * 查询指定用户的审计日志
     */
    public Page<SysOperationLog> getUserAuditLogs(Long userId, int page, int size) {
        return operationLogRepository.findByUserIdOrderByCreateTimeDesc(userId, PageRequest.of(page, size));
    }

    /**
     * 从当前HTTP请求中提取IP和User-Agent
     */
    private void fillRequestInfo(SysOperationLog logEntry) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                logEntry.setIp(ip);
                String ua = request.getHeader("User-Agent");
                if (ua != null && ua.length() > 500) {
                    ua = ua.substring(0, 500);
                }
                logEntry.setUserAgent(ua);
            } else {
                logEntry.setIp("system");
            }
        } catch (Exception e) {
            logEntry.setIp("unknown");
        }
    }

    /**
     * 截断过长的详情信息，防止数据库字段溢出
     */
    private String truncateDetail(String detail) {
        if (detail == null) return null;
        return detail.length() > 2000 ? detail.substring(0, 2000) : detail;
    }
}
