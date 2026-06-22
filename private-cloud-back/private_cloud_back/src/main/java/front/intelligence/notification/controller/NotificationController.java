package front.intelligence.notification.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.intelligence.notification.dto.NotificationDTO;
import front.intelligence.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public Result<List<NotificationDTO>> getNotifications(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<NotificationDTO> notifications = notificationService.getNotifications(userId);
        return Result.success(notifications);
    }

    @GetMapping("/unread")
    public Result<List<NotificationDTO>> getUnreadNotifications(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<NotificationDTO> notifications = notificationService.getUnreadNotifications(userId);
        return Result.success(notifications);
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> getUnreadCount(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        long count = notificationService.getUnreadCount(userId);
        return Result.success(Map.of("count", count, "unreadCount", count));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(Authentication authentication, @PathVariable Long id) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllAsRead(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(Authentication authentication, @PathVariable Long id) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        notificationService.deleteNotification(id, userId);
        return Result.success();
    }

    @DeleteMapping("/clear-read")
    public Result<Void> clearReadNotifications(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        notificationService.clearReadNotifications(userId);
        return Result.success();
    }
}