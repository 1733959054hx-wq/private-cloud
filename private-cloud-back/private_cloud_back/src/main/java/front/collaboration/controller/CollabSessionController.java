package front.collaboration.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.collaboration.entity.CollabSession;
import front.collaboration.service.CollabSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/collab")
public class CollabSessionController {

    @Autowired
    private CollabSessionService collabSessionService;

    @PostMapping
    public Result<CollabSession> createSession(Authentication authentication,
                                                 @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        String roomName = body.get("roomName") != null ? body.get("roomName").toString() : "";
        String permissionMode = body.get("permissionMode") != null
                ? body.get("permissionMode").toString() : "editable";

        CollabSession session = collabSessionService.createSession(roomName, userId, permissionMode);
        return Result.success(session);
    }

    @GetMapping("/document/{documentId}/active")
    public Result<CollabSession> getActiveSession(@PathVariable Long documentId) {
        CollabSession session = collabSessionService.getActiveSession(documentId);
        return Result.success(session);
    }

    @GetMapping("/document/{documentId}")
    public Result<List<CollabSession>> getDocumentSessions(@PathVariable Long documentId) {
        List<CollabSession> sessions = collabSessionService.getDocumentSessions(documentId);
        return Result.success(sessions);
    }

    @GetMapping("/mine")
    public Result<List<CollabSession>> getMyActiveSessions(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<CollabSession> sessions = collabSessionService.getMyActiveSessions(userId);
        return Result.success(sessions);
    }

    @DeleteMapping("/{id}")
    public Result<Void> closeSession(@PathVariable Long id) {
        collabSessionService.closeSession(id);
        return Result.success();
    }

    @PutMapping("/{id}/permission")
    public Result<Void> updatePermission(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        String permissionMode = body.get("permissionMode");
        collabSessionService.updatePermissionMode(id, permissionMode);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<CollabSession> getSession(@PathVariable Long id) {
        CollabSession session = collabSessionService.getSessionById(id);
        return Result.success(session);
    }

    @GetMapping("/{id}/content")
    public Result<String> getContent(@PathVariable Long id) {
        String content = collabSessionService.getContent(id);
        return Result.success(content);
    }

    @PutMapping("/{id}/content")
    public Result<Void> saveContent(@PathVariable Long id,
                                     @RequestBody Map<String, String> body) {
        String content = body.get("content");
        collabSessionService.saveContent(id, content != null ? content : "");
        return Result.success();
    }
}
