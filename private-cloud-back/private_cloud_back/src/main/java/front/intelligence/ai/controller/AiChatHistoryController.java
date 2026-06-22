package front.intelligence.ai.controller;

import front.hxconfig.JwtUtil;
import front.hxconfig.Result;
import front.intelligence.ai.entity.AiChatHistory;
import front.intelligence.ai.service.AiChatHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/ai/history")
public class AiChatHistoryController {

    @Autowired
    private AiChatHistoryService chatHistoryService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getCurrentUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtUtil.getUserIdFromToken(token);
    }

    @PostMapping
    public Result<AiChatHistory> saveMessage(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        String sessionId = body.get("sessionId");
        String role = body.get("role");
        String content = body.get("content");
        String model = body.get("model");
        AiChatHistory saved = chatHistoryService.saveMessage(userId, sessionId, role, content, model);
        return Result.success(saved);
    }

    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> getUserSessions(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<Map<String, Object>> sessions = chatHistoryService.getUserSessions(userId);
        return Result.success(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    public Result<List<AiChatHistory>> getSessionMessages(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<AiChatHistory> messages = chatHistoryService.getSessionMessages(userId, sessionId);
        return Result.success(messages);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        chatHistoryService.deleteSession(userId, sessionId);
        return Result.success();
    }

    @DeleteMapping("/messages/{messageId}")
    public Result<Void> deleteMessage(
            @PathVariable Long messageId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        chatHistoryService.deleteMessage(userId, messageId);
        return Result.success();
    }

    @DeleteMapping("/sessions/{sessionId}/clear")
    public Result<Void> clearSessionMessages(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        chatHistoryService.clearAllMessages(userId, sessionId);
        return Result.success();
    }
}
