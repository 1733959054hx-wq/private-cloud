package front.intelligence.signature.repository;

import front.intelligence.signature.entity.SignatureRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignatureRecordRepository extends JpaRepository<SignatureRecord, Long> {

    List<SignatureRecord> findByDocumentIdOrderBySignTimeDesc(Long documentId);

    List<SignatureRecord> findBySignerIdOrderBySignTimeDesc(Long signerId);

    boolean existsByDocumentIdAndSignerId(Long documentId, Long signerId);
}
