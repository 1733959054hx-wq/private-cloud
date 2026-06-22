package front.workspace.documentspace.service;

import front.system.entity.SysOperationLog;
import front.system.entity.SysRole;
import front.system.entity.SysUser;
import front.system.entity.SysUserRole;
import front.system.repository.SysOperationLogRepository;
import front.system.repository.SysRoleRepository;
import front.system.repository.SysUserRepository;
import front.system.repository.SysUserRoleRepository;
import front.workspace.documentspace.dto.FileDTO;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.documentspace.repository.DocFileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DocFileService 单元测试
 * 验证跨部门文件转移权限控制和操作日志记录
 */
@ExtendWith(MockitoExtension.class)
class DocFileServiceTest {

    @Mock
    private DocFileRepository fileRepository;

    @Mock
    private DocFileVersionRepository fileVersionRepository;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private SysUserRoleRepository sysUserRoleRepository;

    @Mock
    private SysRoleRepository sysRoleRepository;

    @Mock
    private SysOperationLogRepository operationLogRepository;

    @InjectMocks
    private DocFileService docFileService;

    private DocFile testFile;
    private SysUser adminUser;
    private SysUser deptAdminUser;
    private SysUser normalUser;

    @BeforeEach
    void setUp() {
        testFile = new DocFile();
        testFile.setId(1L);
        testFile.setFileName("测试文件.pdf");
        testFile.setDepartmentId(10L);
        testFile.setDirectoryId(100L);
        testFile.setDeleted(0);
        testFile.setStatus(1);

        adminUser = new SysUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setDepartmentId(99L);
        adminUser.setDeleted(0);
        adminUser.setStatus(1);

        deptAdminUser = new SysUser();
        deptAdminUser.setId(50L);
        deptAdminUser.setUsername("deptadmin");
        deptAdminUser.setDepartmentId(10L);
        deptAdminUser.setDeleted(0);
        deptAdminUser.setStatus(1);

        normalUser = new SysUser();
        normalUser.setId(100L);
        normalUser.setUsername("normal");
        normalUser.setDepartmentId(10L);
        normalUser.setDeleted(0);
        normalUser.setStatus(1);
    }

    // ===== 跨部门转移测试 =====

    @Test
    @DisplayName("同部门内转移文件无需跨部门权限")
    void transferFile_sameDepartment_noPermissionNeeded() {
        // Arrange
        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(fileRepository.save(any(DocFile.class))).thenReturn(testFile);

        // Act - 同部门转移（部门10 -> 部门10）
        FileDTO result = docFileService.transferFile(1L, 200L, 10L, 100L);

        // Assert
        assertNotNull(result);
        verify(fileRepository).save(any(DocFile.class));
        // 不应有跨部门操作日志
        verify(operationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("ROLE_ADMIN 可以跨部门转移任意文件")
    void transferFile_adminCanTransferCrossDepartment() {
        // Arrange
        SysRole adminRole = new SysRole();
        adminRole.setId(1L);
        adminRole.setRoleCode("ROLE_ADMIN");

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(1L);
        userRole.setRoleId(1L);

        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserRoleRepository.findByUserId(1L)).thenReturn(Collections.singletonList(userRole));
        when(sysRoleRepository.findById(1L)).thenReturn(Optional.of(adminRole));
        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(fileRepository.save(any(DocFile.class))).thenReturn(testFile);

        // Act - 管理员将文件从部门10转移到部门20
        FileDTO result = docFileService.transferFile(1L, null, 20L, 1L);

        // Assert
        assertNotNull(result);
        // 验证审计日志被记录
        verify(operationLogRepository).save(argThat(log ->
                "FILE_TRANSFER_CROSS_DEPT".equals(log.getOperation())
        ));
    }

    @Test
    @DisplayName("ROLE_DEPT_ADMIN 可以将本部门文件跨部门转移")
    void transferFile_deptAdminCanTransferOwnDeptFiles() {
        // Arrange
        SysRole deptAdminRole = new SysRole();
        deptAdminRole.setId(3L);
        deptAdminRole.setRoleCode("ROLE_DEPT_ADMIN");

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(50L);
        userRole.setRoleId(3L);

        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserRoleRepository.findByUserId(50L)).thenReturn(Collections.singletonList(userRole));
        when(sysRoleRepository.findById(3L)).thenReturn(Optional.of(deptAdminRole));
        when(sysUserRepository.findById(50L)).thenReturn(Optional.of(deptAdminUser));
        when(fileRepository.save(any(DocFile.class))).thenReturn(testFile);

        // Act - 部门管理员将本部门（部门10）文件转移到部门20
        FileDTO result = docFileService.transferFile(1L, null, 20L, 50L);

        // Assert
        assertNotNull(result);
        verify(operationLogRepository).save(any(SysOperationLog.class));
    }

    @Test
    @DisplayName("ROLE_DEPT_ADMIN 不能将其他部门的文件跨部门转移")
    void transferFile_deptAdminCannotTransferOtherDeptFiles() {
        // Arrange
        testFile.setDepartmentId(20L); // 文件属于部门20

        SysRole deptAdminRole = new SysRole();
        deptAdminRole.setId(3L);
        deptAdminRole.setRoleCode("ROLE_DEPT_ADMIN");

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(50L);
        userRole.setRoleId(3L);

        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserRoleRepository.findByUserId(50L)).thenReturn(Collections.singletonList(userRole));
        when(sysRoleRepository.findById(3L)).thenReturn(Optional.of(deptAdminRole));
        when(sysUserRepository.findById(50L)).thenReturn(Optional.of(deptAdminUser));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> docFileService.transferFile(1L, null, 30L, 50L));
        assertTrue(exception.getMessage().contains("只能转移本部门"));
    }

    @Test
    @DisplayName("普通用户不能跨部门转移文件")
    void transferFile_normalUserCannotTransferCrossDepartment() {
        // Arrange
        SysRole userRole = new SysRole();
        userRole.setId(2L);
        userRole.setRoleCode("ROLE_USER");

        SysUserRole ur = new SysUserRole();
        ur.setUserId(100L);
        ur.setRoleId(2L);

        when(fileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(sysUserRoleRepository.findByUserId(100L)).thenReturn(Collections.singletonList(ur));
        when(sysRoleRepository.findById(2L)).thenReturn(Optional.of(userRole));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> docFileService.transferFile(1L, null, 20L, 100L));
        assertTrue(exception.getMessage().contains("无跨部门转移权限"));
    }

    @Test
    @DisplayName("文件不存在时转移应抛出异常")
    void transferFile_fileNotFound_throwsException() {
        // Arrange
        when(fileRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> docFileService.transferFile(999L, null, 20L, 1L));
    }
}
