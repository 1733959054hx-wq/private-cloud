package front.workspace.documentspace.repository;

import front.workspace.documentspace.entity.RecycleBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecycleBinRepository extends JpaRepository<RecycleBin, Long> {

    List<RecycleBin> findByDeletedByOrderByCreateTimeDesc(Long deletedBy);

    Optional<RecycleBin> findByItemTypeAndItemId(String itemType, Long itemId);

    List<RecycleBin> findByExpireTimeBefore(LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RecycleBin r WHERE r.itemType = :itemType AND r.itemId = :itemId")
    void deleteByItemTypeAndItemId(String itemType, Long itemId);
}
