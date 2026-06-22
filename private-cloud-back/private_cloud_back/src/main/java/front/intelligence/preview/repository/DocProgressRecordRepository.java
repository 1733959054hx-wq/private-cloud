package front.intelligence.preview.repository;

import front.intelligence.preview.entity.DocProgressRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocProgressRecordRepository extends JpaRepository<DocProgressRecord, Long> {
    Optional<DocProgressRecord> findByUserIdAndFileIdAndProgressType(Long userId, Long fileId, Integer progressType);
}
