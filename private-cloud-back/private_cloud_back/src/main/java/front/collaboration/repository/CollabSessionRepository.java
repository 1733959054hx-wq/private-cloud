package front.collaboration.repository;

import front.collaboration.entity.CollabSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollabSessionRepository extends JpaRepository<CollabSession, Long> {

    Optional<CollabSession> findBySessionId(String sessionId);

    Optional<CollabSession> findByDocumentIdAndStatus(Long documentId, Integer status);

    List<CollabSession> findByOwnerIdAndStatus(Long ownerId, Integer status);

    List<CollabSession> findByDocumentId(Long documentId);
}
