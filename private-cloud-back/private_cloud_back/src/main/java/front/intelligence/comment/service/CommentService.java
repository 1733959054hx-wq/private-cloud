package front.intelligence.comment.service;

import front.hxconfig.NotificationHandler;
import front.intelligence.comment.dto.CommentDTO;
import front.intelligence.comment.entity.DocComment;
import front.intelligence.comment.repository.DocCommentRepository;
import front.system.entity.SysOperationLog;
import front.system.entity.SysUser;
import front.system.repository.SysOperationLogRepository;
import front.system.repository.SysUserRepository;
import front.system.service.SysUserService;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocDirectoryRepository;
import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CommentService {

    @Autowired
    private DocCommentRepository commentRepository;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private NotificationHandler notificationHandler;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private SysOperationLogRepository operationLogRepository;

    @Autowired
    private DocDirectoryRepository directoryRepository;

    public List<CommentDTO> getFileComments(Long fileId, Long currentUserId) {
        List<DocComment> comments = commentRepository.findByFileIdAndDeletedOrderByCreateTimeDesc(fileId, 0);
        return comments.stream().map(c -> toDTO(c, currentUserId)).collect(Collectors.toList());
    }

    @Transactional
    public CommentDTO addComment(CommentDTO dto, Long userId) {
        DocComment comment = new DocComment();
        comment.setFileId(dto.getFileId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        comment.setMentions(dto.getMentions());
        comment.setQuoteText(dto.getQuoteText());
        comment.setDeleted(0);

        // 从数据库获取文件所属部门ID，写入评论的冗余字段
        DocFile file = fileRepository.findById(dto.getFileId()).orElse(null);
        if (file != null) {
            comment.setDepartmentId(file.getDepartmentId());
        }

        DocComment saved = commentRepository.save(comment);

        if (dto.getMentions() != null && !dto.getMentions().trim().isEmpty()) {
            sendMentionNotifications(saved, userId);
        }

        return toDTO(saved, userId);
    }

    @Transactional
    public CommentDTO updateComment(Long id, String content, Long userId) {
        DocComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("评论不存在"));
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权编辑此评论");
        }
        comment.setContent(content);
        DocComment saved = commentRepository.save(comment);
        return toDTO(saved, userId);
    }

    /**
     * 基于角色的评论删除权限控制：
     * 1. 评论作者本人可删除自己的评论（24小时内）
     * 2. 文档所有者（uploaderId）可删除该文档下的任意评论
     * 3. ROLE_ADMIN 可删除任意评论
     * 4. ROLE_DEPT_ADMIN 可删除本部门文档下的评论（评论的 departmentId 与用户部门一致）
     * 5. 删除操作记录审计日志（操作人、时间、评论内容等）
     */
    @Transactional
    public void deleteComment(Long id, Long userId, boolean isOwner) {
        DocComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("评论不存在"));

        boolean isAuthor = comment.getUserId().equals(userId);
        boolean isFileOwner = false;
        boolean isAdmin = false;
        boolean isDeptAdmin = false;

        DocFile file = fileRepository.findById(comment.getFileId()).orElse(null);
        if (file != null && file.getUploaderId() != null && file.getUploaderId().equals(userId)) {
            isFileOwner = true;
        }

        List<String> roleCodes = sysUserService.getUserRoleCodes(userId);
        if (roleCodes.contains("ROLE_ADMIN")) {
            isAdmin = true;
        }
        if (roleCodes.contains("ROLE_DEPT_ADMIN")) {
            isDeptAdmin = true;
        }

        if (isAuthor) {
            if (comment.getCreateTime() != null) {
                LocalDateTime deadline = comment.getCreateTime().plusHours(24);
                if (LocalDateTime.now().isAfter(deadline)) {
                    throw new RuntimeException("评论发布已超过24小时，无法删除");
                }
            }
        } else if (isFileOwner) {
            // 文档所有者可删除任意评论，无24小时限制
        } else if (isAdmin) {
            // ROLE_ADMIN 可删除任意评论，无24小时限制
        } else if (isDeptAdmin) {
            Long commentDeptId = comment.getDepartmentId();
            if (commentDeptId == null && file != null) {
                commentDeptId = file.getDepartmentId();
            }
            SysUser currentUser = sysUserRepository.findById(userId).orElse(null);
            if (currentUser == null || currentUser.getDepartmentId() == null
                    || !currentUser.getDepartmentId().equals(commentDeptId)) {
                throw new RuntimeException("无权删除此评论，部门管理员只能删除本部门文档下的评论");
            }
        } else {
            throw new RuntimeException("无权删除此评论");
        }

        String originalContent = comment.getContent();
        Long commentUserId = comment.getUserId();
        Long fileId = comment.getFileId();

        String reason;
        if (isAuthor) {
            reason = "评论作者自行删除";
        } else if (isFileOwner) {
            reason = "文档所有者删除";
        } else if (isAdmin) {
            reason = "管理员删除";
        } else {
            reason = "部门管理员删除";
        }

        comment.setDeleted(1);
        comment.setDeletedBy(userId);
        comment.setDeleteReason(reason);
        commentRepository.save(comment);

        logCommentDelete(userId, id, fileId, commentUserId, originalContent, reason);
    }

    private void logCommentDelete(Long operatorId, Long commentId, Long fileId,
                                   Long commentAuthorId, String originalContent, String reason) {
        try {
            SysOperationLog log = new SysOperationLog();
            log.setUserId(operatorId);
            log.setOperation("COMMENT_DELETE");
            log.setDetail(String.format("删除评论[id=%d, fileId=%d, 原作者=%d, 内容摘要=%s, 原因=%s]",
                    commentId, fileId, commentAuthorId,
                    originalContent != null && originalContent.length() > 100
                            ? originalContent.substring(0, 100) + "..."
                            : originalContent,
                    reason));
            log.setCreateTime(LocalDateTime.now());
            operationLogRepository.save(log);
        } catch (Exception e) {
            // 审计日志写入失败不应影响主流程
        }
    }

    private void sendMentionNotifications(DocComment comment, Long fromUserId) {
        DocFile file = fileRepository.findById(comment.getFileId()).orElse(null);
        if (file == null) {
            System.out.println("[通知调试] 文件不存在, fileId=" + comment.getFileId());
            return;
        }
        String fileName = file.getFileName();

        String fromUsername = "用户" + fromUserId;
        try {
            SysUser fromUser = sysUserRepository.findById(fromUserId).orElse(null);
            if (fromUser != null && fromUser.getRealName() != null) {
                fromUsername = fromUser.getRealName();
            }
        } catch (Exception ignored) {}

        Integer spaceType = file.getSpaceType();
        Long departmentId = file.getDepartmentId();
        Long directoryId = file.getDirectoryId();
        System.out.println("[通知调试] fileId=" + file.getId() + " spaceType=" + spaceType + " departmentId=" + departmentId + " directoryId=" + directoryId + " mentions=" + comment.getMentions());

        String[] mentionedIds = comment.getMentions().split(",");
        for (String mentionedIdStr : mentionedIds) {
            try {
                Long mentionedUserId = Long.parseLong(mentionedIdStr.trim());
                boolean canAccess = canUserAccessFile(mentionedUserId, spaceType, departmentId, directoryId);
                System.out.println("[通知调试] mentionedUserId=" + mentionedUserId + " canAccess=" + canAccess);
                if (!canAccess) {
                    SysUser mentionedUser = sysUserRepository.findById(mentionedUserId).orElse(null);
                    System.out.println("[通知调试] 用户部门: " + (mentionedUser != null ? mentionedUser.getDepartmentId() : "null") + " 文件部门: " + departmentId);
                    continue;
                }
                System.out.println("[通知调试] 发送@通知给用户 " + mentionedUserId);
                notificationHandler.sendMentionNotification(
                        mentionedUserId,
                        fromUserId,
                        fromUsername,
                        comment.getFileId(),
                        fileName,
                        comment.getContent()
                );
            } catch (NumberFormatException ignored) {}
        }
    }

    private boolean canUserAccessFile(Long userId, Integer spaceType, Long fileDepartmentId, Long directoryId) {
        if (spaceType == null) {
            return false;
        }
        if (spaceType == 0) {
            return false;
        }
        if (spaceType == 2) {
            return true;
        }
        if (spaceType == 1) {
            Long deptId = fileDepartmentId;
            if (deptId == null && directoryId != null) {
                try {
                    deptId = directoryRepository.findById(directoryId)
                            .map(d -> d.getDepartmentId())
                            .orElse(null);
                } catch (Exception ignored) {}
            }
            if (deptId == null) {
                return true;
            }
            try {
                SysUser user = sysUserRepository.findById(userId).orElse(null);
                return user != null && deptId.equals(user.getDepartmentId());
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private CommentDTO toDTO(DocComment comment, Long currentUserId) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setFileId(comment.getFileId());
        dto.setUserId(comment.getUserId());
        dto.setContent(comment.getContent());
        dto.setParentId(comment.getParentId());
        dto.setMentions(comment.getMentions());
        dto.setDepartmentId(comment.getDepartmentId());
        dto.setCreateTime(comment.getCreateTime());
        dto.setUpdateTime(comment.getUpdateTime());
        dto.setQuoteText(comment.getQuoteText());

        boolean isFileOwner = false;
        if (currentUserId != null) {
            DocFile file = fileRepository.findById(comment.getFileId()).orElse(null);
            if (file != null && file.getUploaderId() != null && file.getUploaderId().equals(currentUserId)) {
                isFileOwner = true;
            }
        }
        dto.setIsFileOwner(isFileOwner);

        boolean canDelete = false;
        if (currentUserId != null) {
            boolean isAuthor = comment.getUserId() != null && comment.getUserId().equals(currentUserId);
            if (isAuthor) {
                if (comment.getCreateTime() != null) {
                    canDelete = !LocalDateTime.now().isAfter(comment.getCreateTime().plusHours(24));
                } else {
                    canDelete = true;
                }
            }
            if (!canDelete) {
                if (isFileOwner) {
                    canDelete = true;
                } else {
                    List<String> roleCodes = sysUserService.getUserRoleCodes(currentUserId);
                    if (roleCodes.contains("ROLE_ADMIN")) {
                        canDelete = true;
                    } else if (roleCodes.contains("ROLE_DEPT_ADMIN")) {
                        Long commentDeptId = comment.getDepartmentId();
                        if (commentDeptId == null) {
                            DocFile file = fileRepository.findById(comment.getFileId()).orElse(null);
                            if (file != null) {
                                commentDeptId = file.getDepartmentId();
                            }
                        }
                        SysUser currentUser = sysUserRepository.findById(currentUserId).orElse(null);
                        if (currentUser != null && currentUser.getDepartmentId() != null
                                && currentUser.getDepartmentId().equals(commentDeptId)) {
                            canDelete = true;
                        }
                    }
                }
            }
        }
        dto.setCanDelete(canDelete);

        if (comment.getUserId() != null) {
            try {
                SysUser user = sysUserRepository.findById(comment.getUserId()).orElse(null);
                dto.setUsername(user != null ? user.getRealName() : "用户" + comment.getUserId());
            } catch (Exception e) {
                dto.setUsername("用户" + comment.getUserId());
            }
        }

        return dto;
    }
}
