package front.workspace.documentspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.workspace.documentspace.dto.VersionDTO;
import front.workspace.documentspace.service.FileVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/files/{fileId}/versions")
public class FileVersionController {

    @Autowired
    private FileVersionService fileVersionService;

    @GetMapping
    public Result<List<VersionDTO>> getVersionHistory(@PathVariable Long fileId) {
        List<VersionDTO> versions = fileVersionService.getVersionHistory(fileId);
        return Result.success(versions);
    }

    @PostMapping
    public Result<VersionDTO> addVersion(Authentication authentication,
                                          @PathVariable Long fileId,
                                          @RequestParam MultipartFile file,
                                          @RequestParam(required = false) String changeNote) throws Exception {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        VersionDTO version = fileVersionService.addVersion(fileId, file, userId, changeNote);
        return Result.success(version);
    }

    @PostMapping("/rollback")
    public Result<VersionDTO> rollbackVersion(@PathVariable Long fileId,
                                               @RequestBody Map<String, Object> body) {
        Integer targetVersion = Integer.valueOf(body.get("targetVersion").toString());
        VersionDTO version = fileVersionService.rollbackVersion(fileId, targetVersion);
        return Result.success(version);
    }
}
