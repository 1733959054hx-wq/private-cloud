package front.workspace.documentspace.repository;

import front.workspace.documentspace.entity.DocShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocShareLinkRepository extends JpaRepository<DocShareLink, Long> {

    Optional<DocShareLink> findByToken(String token);

    List<DocShareLink> findByFileIdAndStatus(Long fileId, Integer status);

    List<DocShareLink> findByCreatorIdAndStatus(Long creatorId, Integer status);

    List<DocShareLink> findByCreatorIdOrderByCreateTimeDesc(Long creatorId);

    List<DocShareLink> findByFileId(Long fileId);
}
