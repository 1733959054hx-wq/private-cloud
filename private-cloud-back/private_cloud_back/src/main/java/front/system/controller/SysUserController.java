package front.system.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import front.system.entity.SysDepartment;
import front.system.entity.SysUser;
import front.system.repository.SysDepartmentRepository;
import front.system.service.SysUserService;
import front.system.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/front/users")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysDepartmentRepository departmentRepository;

    @Autowired
    private DocFileRepository docFileRepository;

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping("/departments")
    public Result<List<SysDepartment>> getAllDepartments() {
        List<SysDepartment> departments = departmentRepository.findAll();
        return Result.success(departments);
    }

    @GetMapping
    public Result<List<Map<String, Object>>> getAllUsers() {
        List<SysUser> users = sysUserService.getAllActiveUsers();
        List<Map<String, Object>> result = users.stream().map(this::toUserMap).collect(Collectors.toList());
        return Result.success(result);
    }

    @GetMapping("/mentionable")
    public Result<List<Map<String, Object>>> getMentionableUsers(
            @RequestParam Long fileId,
            Authentication authentication) {
        Long currentUserId = AuthUtil.getUserId(authentication);

        DocFile file = docFileRepository.findById(fileId).orElse(null);
        if (file == null) {
            return Result.error("文件不存在");
        }

        Integer spaceType = file.getSpaceType();
        Long departmentId = file.getDepartmentId();

        if (spaceType == null || spaceType == 0) {
            return Result.success(List.of());
        }

        List<SysUser> users;
        if (spaceType == 1 && departmentId != null) {
            users = sysUserService.getAllActiveUsers().stream()
                    .filter(u -> departmentId.equals(u.getDepartmentId()))
                    .collect(Collectors.toList());
        } else {
            users = sysUserService.getAllActiveUsers();
        }

        List<Map<String, Object>> result = users.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(this::toUserMap)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    private Map<String, Object> toUserMap(SysUser u) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("realName", u.getRealName());
        map.put("email", u.getEmail());
        map.put("phone", u.getPhone());
        map.put("avatar", u.getAvatar());
        map.put("departmentId", u.getDepartmentId());
        map.put("status", u.getStatus());
        if (u.getDepartmentId() != null) {
            departmentRepository.findById(u.getDepartmentId())
                    .ifPresent(dept -> map.put("departmentName", dept.getDeptName()));
        }
        if (!map.containsKey("departmentName")) {
            map.put("departmentName", null);
        }
        return map;
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getUserById(@PathVariable Long id) {
        SysUser user = sysUserService.getAllActiveUsers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return Result.success(toUserMap(user));
    }

    @PostMapping
    public Result<SysUser> createUser(@RequestBody Map<String, Object> body, Authentication authentication) {
        Long operatorId = AuthUtil.getUserId(authentication);
        SysUser user = new SysUser();
        user.setUsername(body.get("username").toString());
        user.setPassword(body.get("password").toString());
        user.setRealName(body.get("realName") != null ? body.get("realName").toString() : null);
        user.setEmail(body.get("email") != null ? body.get("email").toString() : null);
        user.setPhone(body.get("phone") != null ? body.get("phone").toString() : null);
        user.setDepartmentId(body.get("departmentId") != null
                ? Long.valueOf(body.get("departmentId").toString()) : null);

        @SuppressWarnings("unchecked")
        List<Long> roleIds = body.get("roleIds") != null
                ? (List<Long>) body.get("roleIds") : List.of(2L);

        SysUser created = sysUserService.createUser(user, roleIds);
        created.setPassword(null);

        // 审计日志：创建用户
        auditLogService.logAsync(AuditLogService.MODULE_USER, AuditLogService.OP_CREATE,
                "user", created.getId(),
                "创建用户: " + created.getUsername() + ", 角色ID: " + roleIds,
                AuditLogService.STATUS_SUCCESS, operatorId, null);

        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<SysUser> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                       Authentication authentication) {
        Long operatorId = AuthUtil.getUserId(authentication);
        SysUser updates = new SysUser();
        updates.setRealName(body.get("realName") != null ? body.get("realName").toString() : null);
        updates.setEmail(body.get("email") != null ? body.get("email").toString() : null);
        updates.setPhone(body.get("phone") != null ? body.get("phone").toString() : null);
        updates.setAvatar(body.get("avatar") != null ? body.get("avatar").toString() : null);
        updates.setDepartmentId(body.get("departmentId") != null
                ? Long.valueOf(body.get("departmentId").toString()) : null);
        updates.setStatus(body.get("status") != null
                ? Integer.valueOf(body.get("status").toString()) : null);

        @SuppressWarnings("unchecked")
        List<Long> roleIds = body.get("roleIds") != null
                ? (List<Long>) body.get("roleIds") : null;

        SysUser updated = sysUserService.updateUser(id, updates, roleIds);
        updated.setPassword(null);

        // 审计日志：更新用户
        String detail = "更新用户ID: " + id;
        if (roleIds != null) {
            detail += ", 变更角色ID: " + roleIds;
            auditLogService.logAsync(AuditLogService.MODULE_ROLE, AuditLogService.OP_ROLE_CHANGE,
                    "user", id, detail, AuditLogService.STATUS_SUCCESS, operatorId, null);
        }
        if (updates.getStatus() != null) {
            detail += ", 状态变更为: " + updates.getStatus();
            auditLogService.logAsync(AuditLogService.MODULE_USER, AuditLogService.OP_STATUS_CHANGE,
                    "user", id, "用户状态变更为: " + updates.getStatus(),
                    AuditLogService.STATUS_SUCCESS, operatorId, null);
        }
        auditLogService.logAsync(AuditLogService.MODULE_USER, AuditLogService.OP_UPDATE,
                "user", id, detail, AuditLogService.STATUS_SUCCESS, operatorId, null);

        return Result.success(updated);
    }

    @PutMapping("/{id}/password")
    public Result<Void> changePassword(Authentication authentication,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        Long operatorId = AuthUtil.getUserId(authentication);
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        try {
            sysUserService.changePassword(id, oldPassword, newPassword);
            // 审计日志：密码修改成功
            auditLogService.logAsync(AuditLogService.MODULE_USER, AuditLogService.OP_PASSWORD_CHANGE,
                    "user", id, "修改用户密码", AuditLogService.STATUS_SUCCESS, operatorId, null);
            return Result.success();
        } catch (RuntimeException e) {
            // 审计日志：密码修改失败
            auditLogService.logAsync(AuditLogService.MODULE_USER, AuditLogService.OP_PASSWORD_CHANGE,
                    "user", id, "修改密码失败: " + e.getMessage(), AuditLogService.STATUS_FAIL,
                    operatorId, null);
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        Long operatorId = AuthUtil.getUserId(authentication);
        sysUserService.deleteUser(id);
        // 审计日志：删除用户
        auditLogService.logAsync(AuditLogService.MODULE_USER, AuditLogService.OP_DELETE,
                "user", id, "删除用户ID: " + id, AuditLogService.STATUS_SUCCESS, operatorId, null);
        return Result.success();
    }

    /**
     * 获取当前用户的工作台布局
     * GET /api/front/users/layout
     */
    @GetMapping("/layout")
    public Result<Map<String, Object>> getWorkspaceLayout(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        SysUser user = sysUserService.findById(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceLayout", user != null ? user.getWorkspaceLayout() : null);
        return Result.success(result);
    }

    /**
     * 保存当前用户的工作台布局
     * PUT /api/front/users/layout
     */
    @PutMapping("/layout")
    public Result<Void> saveWorkspaceLayout(Authentication authentication, @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        String layoutJson = body.get("workspaceLayout") != null ? body.get("workspaceLayout").toString() : null;
        sysUserService.updateWorkspaceLayout(userId, layoutJson);
        return Result.success();
    }
}