package front.workspace.documentspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.system.entity.SysUser;
import front.system.service.SysUserService;
import front.workspace.documentspace.service.ChunkUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/front/files/upload")
public class ChunkUploadController {

    @Autowired
    private ChunkUploadService chunkUploadService;

    @Autowired
    private SysUserService sysUserService;

    @PostMapping("/init")
    public Result<Map<String, Object>> createUploadTask(
            Authentication authentication,
            @RequestParam String fileId,
            @RequestParam String fileName,
            @RequestParam Integer totalChunks,
            @RequestParam Long fileSize,
            @RequestParam(required = false) Long directoryId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer spaceType,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Long updateFileId) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        if (departmentId == null && (spaceType == null || spaceType == 0)) {
            SysUser user = sysUserService.findById(userId);
            if (user != null && user.getDepartmentId() != null) {
                departmentId = user.getDepartmentId();
            }
        }
        if (spaceType == null) spaceType = 0;
        if (spaceId == null && spaceType == 0) spaceId = userId;
        Map<String, Object> result = chunkUploadService.createUploadTask(
                fileId, fileName, totalChunks, fileSize, directoryId, departmentId, userId, mode, updateFileId, spaceType, spaceId);
        return Result.success(result);
    }

    @PostMapping("/chunk")
    public Result<Map<String, Object>> uploadChunk(
            Authentication authentication,
            @RequestParam String fileId,
            @RequestParam Integer chunkIndex,
            @RequestParam MultipartFile chunk) throws Exception {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        Map<String, Object> result = chunkUploadService.uploadChunk(fileId, chunkIndex, chunk, userId);
        return Result.success(result);
    }

    @GetMapping("/resume")
    public Result<Map<String, Object>> queryResumeUpload(
            Authentication authentication,
            @RequestParam String fileId) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        Map<String, Object> result = chunkUploadService.queryResumeUpload(fileId, userId);
        return Result.success(result);
    }
}
