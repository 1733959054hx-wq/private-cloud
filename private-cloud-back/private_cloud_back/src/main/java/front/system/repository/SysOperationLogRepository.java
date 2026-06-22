package front.system.repository;

import front.system.entity.SysOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SysOperationLogRepository extends JpaRepository<SysOperationLog, Long> {

    List<SysOperationLog> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<SysOperationLog> findByOperationOrderByCreateTimeDesc(String operation);

    @Query("SELECT l FROM SysOperationLog l WHERE l.userId = :userId AND l.operation = :operation ORDER BY l.createTime DESC")
    List<SysOperationLog> findByUserIdAndOperation(Long userId, String operation);

    @Query("SELECT CAST(l.createTime AS date) AS date, COUNT(l) AS count FROM SysOperationLog l WHERE l.userId = :userId AND l.createTime >= :since GROUP BY CAST(l.createTime AS date) ORDER BY CAST(l.createTime AS date)")
    List<Object[]> countByDateForUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    // ==================== 审计日志查询 ====================

    Page<SysOperationLog> findByModuleOrderByCreateTimeDesc(String module, Pageable pageable);

    Page<SysOperationLog> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    @Query("SELECT l FROM SysOperationLog l WHERE " +
            "(:module IS NULL OR l.module = :module) AND " +
            "(:operation IS NULL OR l.operation = :operation) AND " +
            "(:userId IS NULL OR l.userId = :userId) AND " +
            "(:status IS NULL OR l.status = :status) AND " +
            "(:startTime IS NULL OR l.createTime >= :startTime) AND " +
            "(:endTime IS NULL OR l.createTime <= :endTime) " +
            "ORDER BY l.createTime DESC")
    Page<SysOperationLog> searchAuditLogs(
            @Param("module") String module,
            @Param("operation") String operation,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
