package front.hxconfig;

import front.intelligence.notification.entity.SysNotification;
import front.intelligence.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationHandler {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @Async
    public void sendToUser(Long userId, String type, String title, String content) {
        SysNotification saved = notificationService.createNotification(
                userId, type, title, content, null, null, null, null, null
        );

        Map<String, Object> notification = new HashMap<>();
        notification.put("id", saved.getId());
        notification.put("type", type);
        notification.put("title", title);
        notification.put("content", content);
        notification.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId),
                "/queue/notifications",
                notification
        );
    }

    @Async
    public void sendMentionNotification(Long mentionedUserId, Long fromUserId, String fromUsername,
                                         Long fileId, String fileName, String commentContent) {
        String title = fromUsername + " 在文档中@提到了你";
        String content = commentContent != null && commentContent.length() > 100
                ? commentContent.substring(0, 100) + "..."
                : commentContent;

        SysNotification saved = notificationService.createNotification(
                mentionedUserId, "MENTION", title, content,
                fromUserId, fromUsername, fileId, fileName, null
        );

        Map<String, Object> notification = new HashMap<>();
        notification.put("id", saved.getId());
        notification.put("type", "MENTION");
        notification.put("title", title);
        notification.put("fromUserId", fromUserId);
        notification.put("fromUsername", fromUsername);
        notification.put("fileId", fileId);
        notification.put("fileName", fileName);
        notification.put("commentContent", content);
        notification.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(
                "/topic/mention/" + mentionedUserId,
                (Object) notification
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(mentionedUserId),
                "/queue/notifications",
                notification
        );
    }

    @Async
    public void sendApprovalNotification(Long userId, String title, String content) {
        sendToUser(userId, "APPROVAL", title, content);
    }

    @Async
    public void sendSystemNotification(Long userId, String title, String content) {
        sendToUser(userId, "SYSTEM", title, content);
    }
}
