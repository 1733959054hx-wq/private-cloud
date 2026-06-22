package front.workspace.documentspace.repository;

import front.workspace.documentspace.entity.FileAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileAccessLogRepository extends JpaRepository<FileAccessLog, Long> {

    @Query("SELECT l FROM FileAccessLog l WHERE l.userId = :userId ORDER BY l.createTime DESC")
    List<FileAccessLog> findByUserIdOrderByCreateTimeDesc(@Param("userId") Long userId);

    @Query("SELECT l FROM FileAccessLog l WHERE l.userId = :userId ORDER BY l.createTime DESC LIMIT :limit")
    List<FileAccessLog> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Query("SELECT l FROM FileAccessLog l WHERE l.userId = :userId AND l.id IN " +
           "(SELECT MAX(l2.id) FROM FileAccessLog l2 WHERE l2.userId = :userId GROUP BY l2.fileId) " +
           "ORDER BY l.createTime DESC LIMIT :limit")
    List<FileAccessLog> findDistinctRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
