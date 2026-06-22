package front.system.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.system.entity.SysOperationLog;
import front.system.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 安全审计日志查询接口
 */
@RestController
@RequestMapping("/api/front/audit-logs")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 分页查询审计日志（支持多条件筛选）
     *
     * @param module    操作模块：AUTH / USER / FILE / ROLE / SYSTEM
     * @param operation 操作类型：LOGIN / CREATE / UPDATE / DELETE / VIEW / DOWNLOAD 等
     * @param userId    操作用户ID
     * @param status    操作结果：success / fail
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      页码（从0开始）
     * @param size      每页大小
     */
    @GetMapping
    public Result<Map<String, Object>> searchAuditLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long currentUserId = AuthUtil.getUserId(authentication);
        if (currentUserId == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        // 限制每页最大100条，防止大量数据查询
        if (size > 100) size = 100;

        Page<SysOperationLog> result = auditLogService.searchAuditLogs(
                module, operation, userId, status, startTime, endTime, page, size);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", result.getNumber());
        response.put("pageSize", result.getSize());
        return Result.success(response);
    }

    /**
     * 查询当前用户的审计日志
     */
    @GetMapping("/my")
    public Result<Map<String, Object>> getMyAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        if (size > 100) size = 100;

        Page<SysOperationLog> result = auditLogService.getUserAuditLogs(userId, page, size);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", result.getNumber());
        response.put("pageSize", result.getSize());
        return Result.success(response);
    }
}
