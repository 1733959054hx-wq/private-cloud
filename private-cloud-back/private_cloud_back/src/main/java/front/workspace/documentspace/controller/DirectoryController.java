package front.workspace.documentspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.system.entity.SysUser;
import front.system.service.SysUserService;
import front.workspace.documentspace.dto.DirectoryDTO;
import front.workspace.documentspace.service.DirectoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/directories")
public class DirectoryController {

    @Autowired
    private DirectoryService directoryService;

    @Autowired
    private SysUserService sysUserService;

    @GetMapping
    public Result<List<DirectoryDTO>> getDirectoryTree(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer spaceType,
            @RequestParam(required = false) Long spaceId,
            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);

        if (spaceType != null && spaceId != null) {
            List<DirectoryDTO> tree = directoryService.getDirectoryTreeBySpace(spaceType, spaceId);
            return Result.success(tree);
        }

        if (departmentId == null && userId != null) {
            SysUser user = sysUserService.findById(userId);
            if (user != null && user.getDepartmentId() != null) {
                departmentId = user.getDepartmentId();
            }
        }
        List<DirectoryDTO> tree = directoryService.getDirectoryTree(departmentId);
        return Result.success(tree);
    }

    @PostMapping
    public Result<DirectoryDTO> createDirectory(Authentication authentication,
                                                 @RequestBody DirectoryDTO dto) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录");
        if (dto.getDepartmentId() == null && (dto.getSpaceType() == null || dto.getSpaceType() == 0)) {
            SysUser user = sysUserService.findById(userId);
            if (user != null && user.getDepartmentId() != null) {
                dto.setDepartmentId(user.getDepartmentId());
            }
        }
        if (dto.getSpaceType() == null) dto.setSpaceType(0);
        if (dto.getSpaceId() == null && userId != null && dto.getSpaceType() != null && dto.getSpaceType() == 0) {
            dto.setSpaceId(userId);
        }
        DirectoryDTO created = directoryService.createDirectory(dto, userId);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<DirectoryDTO> updateDirectory(@PathVariable Long id,
                                                 @RequestBody DirectoryDTO dto) {
        DirectoryDTO updated = directoryService.updateDirectory(id, dto);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDirectory(@PathVariable Long id) {
        directoryService.deleteDirectory(id);
        return Result.success();
    }

    @PutMapping("/{id}/move")
    public Result<DirectoryDTO> moveDirectory(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        Object parentIdObj = body.get("newParentId");
        Long newParentId = null;
        if (parentIdObj != null) {
            newParentId = Long.valueOf(parentIdObj.toString());
        }
        Integer sortOrder = body.get("sortOrder") != null
                ? Integer.valueOf(body.get("sortOrder").toString()) : null;
        DirectoryDTO moved = directoryService.moveDirectory(id, newParentId, sortOrder);
        return Result.success(moved);
    }

    @PutMapping("/{id}/rename")
    public Result<DirectoryDTO> renameDirectory(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body,
                                                 Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        String newName = body.get("newName");
        if (newName == null || newName.trim().isEmpty()) {
            return Result.error("新目录名不能为空");
        }
        try {
            DirectoryDTO renamed = directoryService.renameDirectory(id, newName.trim());
            return Result.success(renamed);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/batch-sort")
    public Result<Void> batchSort(@RequestBody List<Map<String, Object>> sortItems) {
        directoryService.batchSort(sortItems);
        return Result.success();
    }
}
