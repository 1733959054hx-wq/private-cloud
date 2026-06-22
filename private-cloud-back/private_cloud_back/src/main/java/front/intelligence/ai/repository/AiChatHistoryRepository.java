package front.intelligence.ai.repository;

import front.intelligence.ai.entity.AiChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiChatHistoryRepository extends JpaRepository<AiChatHistory, Long> {

    List<AiChatHistory> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<AiChatHistory> findByUserIdAndSessionIdOrderByCreateTimeAsc(Long userId, String sessionId);

    List<AiChatHistory> findBySessionIdOrderByCreateTimeAsc(String sessionId);

    void deleteByUserIdAndSessionId(Long userId, String sessionId);

    List<AiChatHistory> findTop20ByUserIdOrderByCreateTimeDesc(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
