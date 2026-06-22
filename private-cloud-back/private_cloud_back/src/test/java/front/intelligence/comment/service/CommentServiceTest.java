package front.intelligence.comment.service;

import front.intelligence.comment.dto.CommentDTO;
import front.intelligence.comment.entity.DocComment;
import front.intelligence.comment.repository.DocCommentRepository;
import front.hxconfig.NotificationHandler;
import front.system.entity.SysOperationLog;
import front.system.entity.SysRole;
import front.system.entity.SysUser;
import front.system.entity.SysUserRole;
import front.system.repository.SysOperationLogRepository;
import front.system.repository.SysRoleRepository;
import front.system.repository.SysUserRepository;
import front.system.repository.SysUserRoleRepository;
import front.system.service.SysUserService;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocDirectoryRepository;
import front.workspace.documentspace.repository.DocFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CommentService 单元测试
 * 验证基于角色的评论删除权限控制逻辑
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private DocCommentRepository commentRepository;

    @Mock
    private DocFileRepository fileRepository;

    @Mock
    private NotificationHandler notificationHandler;

    @Mock
    private SysUserService sysUserService;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private SysUserRoleRepository sysUserRoleRepository;

    @Mock
    private SysRoleRepository sysRoleRepository;

    @Mock
    private SysOperationLogRepository operationLogRepository;

    @Mock
    private DocDirectoryRepository directoryRepository;

    @InjectMocks
    private CommentService commentService;

    private DocComment testComment;
    private DocFile testFile;

    @BeforeEach
    void setUp() {
        testFile = new DocFile();
        testFile.setId(1L);
        testFile.setFileName("测试文件.pdf");
        testFile.setDepartmentId(10L);

        testComment = new DocComment();
        testComment.setId(100L);
        testComment.setFileId(1L);
        testComment.setUserId(200L);
        testComment.setContent("测试评论内容");
        testComment.setDepartmentId(10L);
        testComment.setDeleted(0);
    }

    // ===== 添加评论测试 =====

    @Test
    @DisplayName("添加评论时应从数据库获取文件所属部门ID并写入评论")
    void addComment_shouldSetDepartmentIdFromFile() {
        // Arrange
        CommentDTO dto = new CommentDTO();
        dto.setFileId(1L);
        dto.setContent("新评论");

        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(commentRepository.save(any(DocComment.class))).thenAnswer(invocation -> {
            DocComment saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // Act
        CommentDTO result = commentService.addComment(dto, 300L);

        // Assert
        verify(commentRepository).save(argThat(comment ->
                comment.getDepartmentId() != null && comment.getDepartmentId().equals(10L)
        ));
    }

    // ===== 评论删除权限控制测试 =====

    @Test
    @DisplayName("评论作者可以删除自己的评论")
    void deleteComment_authorCanDeleteOwnComment() {
        Long authorId = 200L;
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(authorId)).thenReturn(Collections.singletonList("ROLE_USER"));
        when(commentRepository.save(any(DocComment.class))).thenReturn(testComment);

        assertDoesNotThrow(() -> commentService.deleteComment(100L, authorId, false));

        verify(commentRepository).save(argThat(comment ->
                comment.getDeleted() == 1 && comment.getDeletedBy().equals(authorId)
        ));

        verify(operationLogRepository).save(argThat(log ->
                "COMMENT_DELETE".equals(log.getOperation()) && log.getUserId().equals(authorId)
        ));
    }

    @Test
    @DisplayName("非作者且非文档所有者且无管理角色的用户不能删除评论")
    void deleteComment_nonAuthorNonOwnerNonAdminCannotDelete() {
        Long otherUserId = 999L;
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(otherUserId)).thenReturn(Collections.singletonList("ROLE_USER"));
        when(sysUserRepository.findById(otherUserId)).thenReturn(Optional.of(createUser(otherUserId, 99L)));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> commentService.deleteComment(100L, otherUserId, false));
        assertTrue(exception.getMessage().contains("无权删除此评论"));
    }

    @Test
    @DisplayName("文档所有者可以删除该文档下的任意评论")
    void deleteComment_fileOwnerCanDeleteAnyComment() {
        Long ownerId = 300L;
        testFile.setUploaderId(ownerId);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(ownerId)).thenReturn(Collections.singletonList("ROLE_USER"));
        when(commentRepository.save(any(DocComment.class))).thenReturn(testComment);

        assertDoesNotThrow(() -> commentService.deleteComment(100L, ownerId, false));

        verify(commentRepository).save(argThat(comment ->
                comment.getDeleted() == 1 && comment.getDeletedBy().equals(ownerId)
        ));

        verify(operationLogRepository).save(argThat(log ->
                "COMMENT_DELETE".equals(log.getOperation()) &&
                        log.getDetail().contains("文档所有者删除")
        ));
    }

    @Test
    @DisplayName("ROLE_ADMIN 可以删除任意评论")
    void deleteComment_adminCanDeleteAnyComment() {
        Long adminUserId = 1L;
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(adminUserId)).thenReturn(Collections.singletonList("ROLE_ADMIN"));
        when(commentRepository.save(any(DocComment.class))).thenReturn(testComment);

        assertDoesNotThrow(() -> commentService.deleteComment(100L, adminUserId, false));

        verify(operationLogRepository).save(argThat(log ->
                "COMMENT_DELETE".equals(log.getOperation()) &&
                        log.getDetail().contains("管理员删除")
        ));
    }

    @Test
    @DisplayName("ROLE_DEPT_ADMIN 可以删除本部门文档下的评论")
    void deleteComment_deptAdminCanDeleteSameDeptComment() {
        Long deptAdminId = 50L;
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(deptAdminId)).thenReturn(Collections.singletonList("ROLE_DEPT_ADMIN"));
        when(sysUserRepository.findById(deptAdminId)).thenReturn(Optional.of(createUser(deptAdminId, 10L)));
        when(commentRepository.save(any(DocComment.class))).thenReturn(testComment);

        assertDoesNotThrow(() -> commentService.deleteComment(100L, deptAdminId, false));
    }

    @Test
    @DisplayName("ROLE_DEPT_ADMIN 不能删除其他部门文档下的评论")
    void deleteComment_deptAdminCannotDeleteOtherDeptComment() {
        Long deptAdminId = 50L;
        testComment.setDepartmentId(20L);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(deptAdminId)).thenReturn(Collections.singletonList("ROLE_DEPT_ADMIN"));
        when(sysUserRepository.findById(deptAdminId)).thenReturn(Optional.of(createUser(deptAdminId, 10L)));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> commentService.deleteComment(100L, deptAdminId, false));
        assertTrue(exception.getMessage().contains("无权删除此评论"));
    }

    @Test
    @DisplayName("评论departmentId为空时从文件表查询部门ID进行判断")
    void deleteComment_fallbackToFileDepartmentWhenCommentDeptIsNull() {
        Long deptAdminId = 50L;
        testComment.setDepartmentId(null);
        when(commentRepository.findById(100L)).thenReturn(Optional.of(testComment));
        when(sysUserService.getUserRoleCodes(deptAdminId)).thenReturn(Collections.singletonList("ROLE_DEPT_ADMIN"));
        when(sysUserRepository.findById(deptAdminId)).thenReturn(Optional.of(createUser(deptAdminId, 10L)));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));

        assertDoesNotThrow(() -> commentService.deleteComment(100L, deptAdminId, false));
    }

    // ===== 评论查询测试 =====

    @Test
    @DisplayName("获取文件评论列表应返回未删除的评论")
    void getFileComments_shouldReturnNonDeletedComments() {
        when(commentRepository.findByFileIdAndDeletedOrderByCreateTimeDesc(1L, 0))
                .thenReturn(Collections.singletonList(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(anyLong())).thenReturn(Collections.singletonList("ROLE_USER"));
        when(sysUserRepository.findById(200L)).thenReturn(Optional.of(createUser(200L, 10L)));

        List<CommentDTO> result = commentService.getFileComments(1L, 200L);

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getId());
    }

    @Test
    @DisplayName("评论DTO的username应从数据库查询真实姓名")
    void toDTO_shouldQueryRealNameFromDatabase() {
        SysUser commentUser = createUser(200L, 10L);
        commentUser.setRealName("张三");
        when(commentRepository.findByFileIdAndDeletedOrderByCreateTimeDesc(1L, 0))
                .thenReturn(Collections.singletonList(testComment));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserService.getUserRoleCodes(anyLong())).thenReturn(Collections.singletonList("ROLE_USER"));
        when(sysUserRepository.findById(200L)).thenReturn(Optional.of(commentUser));

        List<CommentDTO> result = commentService.getFileComments(1L, 200L);

        assertEquals("张三", result.get(0).getUsername());
    }

    // ===== 辅助方法 =====

    private SysUser createUser(Long userId, Long departmentId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("user" + userId);
        user.setRealName("用户" + userId);
        user.setDepartmentId(departmentId);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }
}
