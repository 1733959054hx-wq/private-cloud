package front.intelligence.ai.repository;

import front.intelligence.ai.entity.DocMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocMetadataRepository extends JpaRepository<DocMetadata, Long> {

    List<DocMetadata> findByFileId(Long fileId);

    @Modifying
    void deleteByFileId(Long fileId);
}
