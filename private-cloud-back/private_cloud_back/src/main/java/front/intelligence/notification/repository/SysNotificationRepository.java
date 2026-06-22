package front.intelligence.notification.repository;

import front.intelligence.notification.entity.SysNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysNotificationRepository extends JpaRepository<SysNotification, Long> {

    List<SysNotification> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<SysNotification> findByUserIdAndIsReadOrderByCreateTimeDesc(Long userId, Integer isRead);

    long countByUserIdAndIsRead(Long userId, Integer isRead);

    @Modifying
    @Query("UPDATE SysNotification n SET n.isRead = 1 WHERE n.userId = :userId AND n.isRead = 0")
    void markAllAsReadByUserId(Long userId);

    void deleteByUserIdAndIsRead(Long userId, Integer isRead);

    void deleteByFileIdAndType(Long fileId, String type);

    List<SysNotification> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
}
