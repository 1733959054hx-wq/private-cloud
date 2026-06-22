package front.intelligence.ai.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.intelligence.ai.entity.GeneratedDoc;
import front.intelligence.ai.service.GeneratedDocService;
import front.system.entity.SysUser;
import front.system.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/front/ai/generated-docs")
public class GeneratedDocController {

    @Autowired
    private GeneratedDocService generatedDocService;

    @Autowired
    private SysUserService sysUserService;

    @GetMapping
    public Result<List<GeneratedDoc>> getGeneratedDocs(Authentication authentication) {
        Long departmentId = null;
        Long userId = AuthUtil.getUserId(authentication);
        if (userId != null) {
            SysUser user = sysUserService.findById(userId);
            if (user != null && user.getDepartmentId() != null) {
                departmentId = user.getDepartmentId();
            }
        }
        List<GeneratedDoc> docs = generatedDocService.getDocsByDepartment(departmentId);
        return Result.success(docs);
    }

    @GetMapping("/my")
    public Result<List<GeneratedDoc>> getMyGeneratedDocs(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<GeneratedDoc> docs = generatedDocService.getDocsByCreator(userId);
        return Result.success(docs);
    }

    @GetMapping("/{id}")
    public Result<GeneratedDoc> getGeneratedDoc(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        GeneratedDoc doc = generatedDocService.getDocById(id);
        if (doc == null) {
            return Result.error("文档不存在");
        }
        // 权限校验：仅创建者或同部门用户可查看
        SysUser user = sysUserService.findById(userId);
        boolean isCreator = userId.equals(doc.getCreatorId());
        boolean isSameDept = user != null && user.getDepartmentId() != null
                && user.getDepartmentId().equals(doc.getDepartmentId());
        if (!isCreator && !isSameDept) {
            return Result.error(403, "无权访问该文档");
        }
        return Result.success(doc);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteGeneratedDoc(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        GeneratedDoc doc = generatedDocService.getDocById(id);
        if (doc == null) {
            return Result.error("文档不存在");
        }
        // 权限校验：仅创建者可删除
        if (!userId.equals(doc.getCreatorId())) {
            return Result.error(403, "无权删除该文档");
        }
        generatedDocService.deleteDoc(id);
        return Result.success();
    }
}
