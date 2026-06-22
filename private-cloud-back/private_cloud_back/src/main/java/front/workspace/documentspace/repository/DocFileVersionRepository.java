package front.workspace.documentspace.repository;

import front.workspace.documentspace.entity.DocFileVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocFileVersionRepository extends JpaRepository<DocFileVersion, Long> {

    List<DocFileVersion> findByFileIdOrderByVersionDesc(Long fileId);

    DocFileVersion findTopByFileIdOrderByVersionDesc(Long fileId);

    DocFileVersion findByFileIdAndVersion(Long fileId, Integer version);
}
