package front.intelligence.notification.service;

import front.intelligence.notification.dto.NotificationDTO;
import front.intelligence.notification.entity.SysNotification;
import front.intelligence.notification.repository.SysNotificationRepository;
import front.system.entity.SysUser;
import front.system.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    @Autowired
    private SysNotificationRepository notificationRepository;

    @Autowired
    private SysUserRepository sysUserRepository;

    public List<NotificationDTO> getNotifications(Long userId) {
        List<SysNotification> list = notificationRepository.findByUserIdOrderByCreateTimeDesc(userId);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<NotificationDTO> getUnreadNotifications(Long userId) {
        List<SysNotification> list = notificationRepository.findByUserIdAndIsReadOrderByCreateTimeDesc(userId, 0);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, 0);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        SysNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification != null && notification.getUserId().equals(userId)) {
            notification.setIsRead(1);
            notification.setReadTime(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        SysNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification != null && notification.getUserId().equals(userId)) {
            notificationRepository.delete(notification);
        }
    }

    @Transactional
    public void clearReadNotifications(Long userId) {
        notificationRepository.deleteByUserIdAndIsRead(userId, 1);
    }

    @Transactional
    public SysNotification createNotification(Long userId, String type, String title, String content,
                                               Long fromUserId, String fromUsername,
                                               Long fileId, String fileName, Long commentId) {
        SysNotification notification = new SysNotification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setFromUserId(fromUserId);
        notification.setFromUsername(fromUsername);
        notification.setFileId(fileId);
        notification.setFileName(fileName);
        notification.setCommentId(commentId);
        notification.setIsRead(0);
        return notificationRepository.save(notification);
    }

    private NotificationDTO toDTO(SysNotification entity) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(entity.getId());
        dto.setType(entity.getType());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setFromUserId(entity.getFromUserId());
        dto.setFromUsername(entity.getFromUsername());
        dto.setFileId(entity.getFileId());
        dto.setFileName(entity.getFileName());
        dto.setCommentId(entity.getCommentId());
        dto.setIsRead(entity.getIsRead());
        dto.setCreateTime(entity.getCreateTime());

        if (entity.getFromUserId() != null && (entity.getFromUsername() == null || entity.getFromUsername().isEmpty())) {
            try {
                SysUser user = sysUserRepository.findById(entity.getFromUserId()).orElse(null);
                dto.setFromUsername(user != null ? user.getRealName() : "用户" + entity.getFromUserId());
            } catch (Exception e) {
                dto.setFromUsername("用户" + entity.getFromUserId());
            }
        }

        return dto;
    }
}
