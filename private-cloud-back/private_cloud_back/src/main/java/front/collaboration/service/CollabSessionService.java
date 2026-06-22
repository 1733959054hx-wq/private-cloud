package front.collaboration.service;

import front.collaboration.entity.CollabSession;
import front.collaboration.repository.CollabSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CollabSessionService {

    @Autowired
    private CollabSessionRepository collabSessionRepository;

    @Transactional
    public CollabSession createSession(String roomName, Long ownerId, String permissionMode) {
        CollabSession session = new CollabSession();
        session.setDocumentId(0L);
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setRoomName(roomName != null && !roomName.isBlank() ? roomName : "房间#" + System.currentTimeMillis() % 10000);
        session.setStatus(0);
        session.setPermissionMode(permissionMode != null ? permissionMode : "editable");
        session.setOwnerId(ownerId);
        session.setContent("");
        return collabSessionRepository.save(session);
    }

    @Transactional
    public void saveContent(Long id, String content) {
        CollabSession session = collabSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("协同会话不存在"));
        session.setContent(content);
        collabSessionRepository.save(session);
    }

    public String getContent(Long id) {
        CollabSession session = collabSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("协同会话不存在"));
        String content = session.getContent();
        return content != null ? content : "";
    }

    public CollabSession getActiveSession(Long documentId) {
        return collabSessionRepository.findByDocumentIdAndStatus(documentId, 0)
                .orElseThrow(() -> new RuntimeException("该文档没有活跃的协同会话"));
    }

    public CollabSession getSessionBySessionId(String sessionId) {
        return collabSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("协同会话不存在"));
    }

    public CollabSession getSessionById(Long id) {
        return collabSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("协同会话不存在"));
    }

    public List<CollabSession> getDocumentSessions(Long documentId) {
        return collabSessionRepository.findByDocumentId(documentId);
    }

    public List<CollabSession> getMyActiveSessions(Long ownerId) {
        return collabSessionRepository.findByOwnerIdAndStatus(ownerId, 0);
    }

    @Transactional
    public void closeSession(Long id) {
        CollabSession session = collabSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("协同会话不存在"));
        session.setStatus(1);
        session.setCloseTime(LocalDateTime.now());
        collabSessionRepository.save(session);
    }

    @Transactional
    public void updatePermissionMode(Long id, String permissionMode) {
        CollabSession session = collabSessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("协同会话不存在"));
        session.setPermissionMode(permissionMode);
        collabSessionRepository.save(session);
    }
}
