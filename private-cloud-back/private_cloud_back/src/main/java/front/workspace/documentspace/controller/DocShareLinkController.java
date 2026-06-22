package front.workspace.documentspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.storage.service.StorageRouter;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.entity.DocShareLink;
import front.workspace.documentspace.repository.DocFileRepository;
import front.workspace.documentspace.service.DocShareLinkService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/front/share-links")
public class DocShareLinkController {

    @Autowired
    private DocShareLinkService shareLinkService;

    @Autowired
    private DocFileRepository docFileRepository;

    @Autowired
    private StorageRouter storageRouter;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 免登录下载分享文件
     * GET /api/front/share-links/download/{token}
     */
    @GetMapping("/download/{token}")
    public void downloadSharedFile(@PathVariable String token, HttpServletResponse response) throws IOException {
        DocShareLink link = shareLinkService.getShareLinkByToken(token);
        if (link == null || link.getStatus() == null || link.getStatus() != 1) {
            response.setStatus(404);
            response.getWriter().write("链接不存在或已关闭");
            return;
        }
        if ("view".equalsIgnoreCase(link.getPermissionType())) {
            response.setStatus(403);
            response.getWriter().write("此链接仅允许预览，不支持下载");
            return;
        }
        DocFile file = docFileRepository.findById(link.getFileId()).orElse(null);
        if (file == null) {
            response.setStatus(404);
            response.getWriter().write("文件不存在");
            return;
        }

        String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
        String encodedName = URLEncoder.encode(file.getFileName(), "UTF-8").replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

        if ("minio".equalsIgnoreCase(storageType)) {
            // MinIO 存储：流式下载
            try (OutputStream os = response.getOutputStream();
                 InputStream is = storageRouter.download(storageType, file.getFilePath())) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            } catch (Exception e) {
                response.setStatus(404);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            }
            return;
        }

        // 本地存储：保持原有逻辑
        java.io.File diskFile = new java.io.File(file.getFilePath());
        if (!diskFile.exists()) {
            diskFile = new java.io.File(System.getProperty("user.dir"), file.getFilePath());
        }
        if (!diskFile.exists()) {
            String clean = file.getFilePath().replaceAll("^[\\\\/.]*uploads?[\\\\/]", "");
            diskFile = new java.io.File(uploadDir, clean);
        }
        if (!diskFile.exists()) {
            response.setStatus(404);
            response.getWriter().write("文件已被删除");
            return;
        }
        response.setHeader("Content-Length", String.valueOf(diskFile.length()));
        try (OutputStream os = response.getOutputStream();
             InputStream is = new FileInputStream(diskFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    @PostMapping
    public Result<DocShareLink> createShareLink(Authentication authentication,
                                                 @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        Long fileId = Long.valueOf(body.get("fileId").toString());
        String password = body.get("password") != null ? body.get("password").toString() : null;
        LocalDateTime expireTime = body.get("expireTime") != null
                ? LocalDateTime.parse(body.get("expireTime").toString())
                : null;
        Integer maxAccess = body.get("maxAccess") != null
                ? Integer.valueOf(body.get("maxAccess").toString())
                : 0;
        String permissionType = body.get("permissionType") != null
                ? body.get("permissionType").toString()
                : "view";

        DocShareLink link = shareLinkService.createShareLink(
                fileId, userId, password, expireTime, maxAccess, permissionType);
        return Result.success(link);
    }

    @GetMapping("/token/{token}")
    public Result<DocShareLink> getShareLinkByToken(@PathVariable String token) {
        DocShareLink link = shareLinkService.getShareLinkByToken(token);
        return Result.success(link);
    }

    @GetMapping("/file/{fileId}")
    public Result<List<DocShareLink>> getShareLinksByFile(@PathVariable Long fileId) {
        List<DocShareLink> links = shareLinkService.getShareLinksByFileId(fileId);
        return Result.success(links);
    }

    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> getMyShareLinks(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<DocShareLink> links = shareLinkService.getActiveShareLinksByCreator(userId);
        return Result.success(enrichWithFileName(links));
    }

    @GetMapping("/mine/all")
    public Result<List<Map<String, Object>>> getAllMyShareLinks(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<DocShareLink> links = shareLinkService.getAllShareLinksByCreator(userId);
        return Result.success(enrichWithFileName(links));
    }

    private List<Map<String, Object>> enrichWithFileName(List<DocShareLink> links) {
        return links.stream().map(link -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", link.getId());
            map.put("fileId", link.getFileId());
            map.put("token", link.getToken());
            map.put("password", link.getPassword());
            map.put("expireTime", link.getExpireTime());
            map.put("maxAccess", link.getMaxAccess());
            map.put("accessCount", link.getAccessCount());
            map.put("permissionType", link.getPermissionType());
            map.put("status", link.getStatus());
            map.put("createTime", link.getCreateTime());
            // 查询文件名
            String fileName = docFileRepository.findById(link.getFileId())
                    .map(DocFile::getFileName)
                    .orElse("未知文件");
            map.put("fileName", fileName);
            return map;
        }).collect(Collectors.toList());
    }

    @DeleteMapping("/{linkId}")
    public Result<Void> cancelShareLink(Authentication authentication, @PathVariable Long linkId) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        shareLinkService.cancelShareLink(linkId, userId);
        return Result.success();
    }

    @DeleteMapping("/{linkId}/force")
    public Result<Void> deleteShareLink(Authentication authentication, @PathVariable Long linkId) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        shareLinkService.deleteShareLinkPermanently(linkId, userId);
        return Result.success();
    }

    @PostMapping("/access/{token}")
    public Result<Void> accessShareLink(@PathVariable String token) {
        shareLinkService.incrementAccessCount(token);
        return Result.success();
    }
}
