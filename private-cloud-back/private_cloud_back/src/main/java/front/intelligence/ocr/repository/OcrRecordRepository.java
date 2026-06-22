package front.intelligence.ocr.repository;

import front.intelligence.ocr.entity.OcrRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OcrRecordRepository extends JpaRepository<OcrRecord, Long> {

    List<OcrRecord> findAllByFileId(Long fileId);

    @Query("SELECT r FROM OcrRecord r WHERE r.fileId = :fileId ORDER BY r.id DESC LIMIT 1")
    Optional<OcrRecord> findByFileId(Long fileId);

    List<OcrRecord> findAllByFileIdAndPageNumber(Long fileId, Integer pageNumber);

    @Query("SELECT r FROM OcrRecord r WHERE r.fileId = :fileId AND r.pageNumber = :pageNumber ORDER BY r.id DESC LIMIT 1")
    Optional<OcrRecord> findByFileIdAndPageNumber(Long fileId, Integer pageNumber);

    List<OcrRecord> findByStatus(Integer status);

    List<OcrRecord> findByFileIdIn(List<Long> fileIds);

    List<OcrRecord> findByFileIdOrderByPageNumberAsc(Long fileId);

    @Modifying
    void deleteByFileId(Long fileId);
}
