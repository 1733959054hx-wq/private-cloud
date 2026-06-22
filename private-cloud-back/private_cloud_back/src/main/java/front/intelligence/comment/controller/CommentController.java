package front.intelligence.comment.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.intelligence.comment.dto.CommentDTO;
import front.intelligence.comment.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/front/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @GetMapping
    public Result<List<CommentDTO>> getFileComments(Authentication authentication,
                                                      @RequestParam Long fileId) {
        Long userId = AuthUtil.getUserId(authentication);
        List<CommentDTO> comments = commentService.getFileComments(fileId, userId);
        return Result.success(comments);
    }

    @PostMapping
    public Result<CommentDTO> addComment(Authentication authentication,
                                          @RequestBody CommentDTO dto) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        CommentDTO created = commentService.addComment(dto, userId);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<CommentDTO> updateComment(Authentication authentication,
                                             @PathVariable Long id,
                                             @RequestBody CommentDTO dto) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        CommentDTO updated = commentService.updateComment(id, dto.getContent(), userId);
        return Result.success(updated);
    }

    /**
     * 删除评论 - 基于角色的权限控制
     * 权限判断逻辑在 CommentService 中实现：
     * 1. 评论作者可删除自己的评论（24小时内）
     * 2. 文档所有者可删除该文档下的任意评论
     * 3. ROLE_ADMIN 可删除任意评论
     * 4. ROLE_DEPT_ADMIN 可删除本部门文档下的评论
     * isOwner 参数保留向后兼容，前端传入仅作为提示参考
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(Authentication authentication,
                                       @PathVariable Long id,
                                       @RequestParam(defaultValue = "false") boolean isOwner) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        try {
            commentService.deleteComment(id, userId, isOwner);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
