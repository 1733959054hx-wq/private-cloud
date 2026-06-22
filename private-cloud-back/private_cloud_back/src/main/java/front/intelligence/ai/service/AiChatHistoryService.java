package front.intelligence.ai.service;

import front.intelligence.ai.entity.AiChatHistory;
import front.intelligence.ai.repository.AiChatHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiChatHistoryService {

    @Autowired
    private AiChatHistoryRepository chatHistoryRepository;

    @Transactional
    public AiChatHistory saveMessage(Long userId, String sessionId, String role, String content, String model) {
        AiChatHistory history = new AiChatHistory();
        history.setUserId(userId);
        history.setSessionId(sessionId);
        history.setRole(role);
        history.setContent(content);
        history.setModel(model);
        return chatHistoryRepository.save(history);
    }

    public List<Map<String, Object>> getUserSessions(Long userId) {
        List<AiChatHistory> allMessages = chatHistoryRepository.findByUserIdOrderByCreateTimeDesc(userId);

        Map<String, List<AiChatHistory>> grouped = allMessages.stream()
                .collect(Collectors.groupingBy(AiChatHistory::getSessionId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (Map.Entry<String, List<AiChatHistory>> entry : grouped.entrySet()) {
            Map<String, Object> session = new HashMap<>();
            session.put("sessionId", entry.getKey());
            List<AiChatHistory> msgs = entry.getValue();
            session.put("lastTime", msgs.get(0).getCreateTime());
            session.put("messageCount", msgs.size());
            msgs.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .findFirst()
                    .ifPresent(m -> session.put("firstQuestion", m.getContent()));
            sessions.add(session);
        }
        return sessions;
    }

    public List<AiChatHistory> getSessionMessages(Long userId, String sessionId) {
        return chatHistoryRepository.findByUserIdAndSessionIdOrderByCreateTimeAsc(userId, sessionId);
    }

    @Transactional
    public void deleteSession(Long userId, String sessionId) {
        chatHistoryRepository.deleteByUserIdAndSessionId(userId, sessionId);
    }

    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        chatHistoryRepository.deleteByIdAndUserId(messageId, userId);
    }

    @Transactional
    public void clearAllMessages(Long userId, String sessionId) {
        chatHistoryRepository.deleteByUserIdAndSessionId(userId, sessionId);
    }
}
