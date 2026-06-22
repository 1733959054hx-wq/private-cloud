package front.workspace.documentspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.system.entity.SysUser;
import front.system.service.SysUserService;
import front.workspace.documentspace.dto.FileDTO;
import front.workspace.documentspace.service.DocFileService;
import front.workspace.documentspace.service.FileAccessLogService;
import front.workspace.documentspace.service.VideoCoverService;
import front.storage.service.StorageRouter;
import front.system.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/files")
public class DocFileController {

    @Autowired
    private DocFileService docFileService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private FileAccessLogService fileAccessLogService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private StorageRouter storageRouter;

    @Autowired
    private VideoCoverService videoCoverService;

    @GetMapping
    public Result<List<FileDTO>> getFilesByDirectory(
            @RequestParam(required = false) Long directoryId,
            @RequestParam(required = false) Integer spaceType,
            @RequestParam(required = false) Long spaceId,
            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);

        if (spaceType != null && spaceId != null) {
            List<FileDTO> files = docFileService.getFilesBySpace(spaceType, spaceId, directoryId, userId);
            return Result.success(files);
        }

        Long departmentId = null;
        if (userId != null) {
            SysUser user = sysUserService.findById(userId);
            if (user != null && user.getDepartmentId() != null) {
                departmentId = user.getDepartmentId();
            }
        }
        List<FileDTO> files;
        if (departmentId != null) {
            files = docFileService.getFilesByDirectoryAndDepartment(directoryId, departmentId, userId);
        } else {
            files = docFileService.getFilesByDirectory(directoryId, userId);
        }
        return Result.success(files);
    }

    /**
     * 分页获取文件列表（解决文件列表一次性加载全部数据问题）
     */
    @GetMapping("/page")
    public Result<Map<String, Object>> getFilesByDirectoryPage(
            @RequestParam(required = false) Long directoryId,
            @RequestParam(required = false) Integer spaceType,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createTime") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) {
            return Result.error(401, "未登录或登录已过期");
        }

        Sort sort = "asc".equalsIgnoreCase(sortOrder)
                ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();
        if (page < 0) page = 0;
        if (size < 1) size = 20;
        // 允许双层分页：后端大分页一次拉取多条数据，前端再切片展示
        if (size > 500) size = 500;
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<FileDTO> filePage;
        if (spaceType != null && spaceId != null) {
            filePage = docFileService.getFilesBySpacePage(spaceType, spaceId, directoryId, userId, pageable);
        } else {
            Long departmentId = null;
            SysUser user = sysUserService.findById(userId);
            if (user != null && user.getDepartmentId() != null) {
                departmentId = user.getDepartmentId();
            }
            if (departmentId != null) {
                filePage = docFileService.getFilesByDirectoryAndDepartmentPage(directoryId, departmentId, userId, pageable);
            } else {
                filePage = docFileService.getFilesByDirectoryPage(directoryId, userId, pageable);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", filePage.getContent());
        result.put("totalElements", filePage.getTotalElements());
        result.put("totalPages", filePage.getTotalPages());
        result.put("number", filePage.getNumber());
        result.put("size", filePage.getSize());
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<FileDTO> getFileById(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId != null) {
            fileAccessLogService.logAccessAsync(userId, id, "preview");
        }
        FileDTO file = docFileService.getFileById(id);
        return Result.success(file);
    }

    /**
     * 记录文件浏览（停留达到 10 秒以上才应调用）
     * POST /api/front/files/{id}/view
     */
    @PostMapping("/{id}/view")
    public Result<Void> recordFileView(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        // 校验用户有权限访问该文件
        if (!docFileService.canAccessFile(id, userId)) {
            return Result.error(403, "无权访问该文件");
        }
        docFileService.incrementViewCount(id);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<FileDTO> updateFile(@PathVariable Long id, @RequestBody FileDTO dto) {
        FileDTO updated = docFileService.updateFile(id, dto);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> softDeleteFile(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        docFileService.softDeleteFile(id);
        // 审计日志：删除文件
        auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_DELETE,
                "file", id, "删除文件", AuditLogService.STATUS_SUCCESS, userId, null);
        return Result.success();
    }

    @PutMapping("/{id}/restore")
    public Result<Void> restoreFile(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        docFileService.restoreFile(id);
        // 审计日志：恢复文件
        auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_RESTORE,
                "file", id, "恢复文件", AuditLogService.STATUS_SUCCESS, userId, null);
        return Result.success();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id, Authentication authentication) {
        docFileService.incrementDownloadCount(id);
        Long userId = AuthUtil.getUserId(authentication);
        if (userId != null) {
            fileAccessLogService.logAccessAsync(userId, id, "download");
            // 审计日志：下载文件
            auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_DOWNLOAD,
                    "file", id, "下载文件", AuditLogService.STATUS_SUCCESS, userId, null);
        }
        FileDTO file = docFileService.getFileById(id);

        // 根据 storage_type 决定读取本地文件或 MinIO
        String storageType = file.getStorageType();
        if (storageType == null) storageType = "local";

        if ("minio".equalsIgnoreCase(storageType)) {
            // MinIO 文件：优先返回预签名 URL 重定向（302）
            String presignedUrl = storageRouter.getPresignedUrl(storageType, file.getFilePath(), Duration.ofMinutes(30));
            if (presignedUrl != null) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, presignedUrl)
                        .build();
            }
            // 预签名 URL 获取失败，降级为后端流式下载
            try {
                InputStream inputStream = storageRouter.download(storageType, file.getFilePath());
                InputStreamResource resource = new InputStreamResource(inputStream);
                String encodedName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8)
                        .replace("+", "%20");
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''" + encodedName)
                        .body(resource);
            } catch (Exception e) {
                return ResponseEntity.notFound().build();
            }
        }

        // 本地文件：保持原有逻辑
        File fileObj = new File(file.getFilePath());
        if (!fileObj.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(fileObj);
        String encodedName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    @GetMapping("/search")
    public Result<List<FileDTO>> searchFiles(@RequestParam String keyword) {
        List<FileDTO> files = docFileService.searchFiles(keyword);
        return Result.success(files);
    }

    /**
     * 获取相关文件推荐
     * GET /api/front/files/{id}/related
     */
    @GetMapping("/{id}/related")
    public Result<List<FileDTO>> getRelatedFiles(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<FileDTO> files = docFileService.getRelatedFiles(id, userId);
        return Result.success(files);
    }

    /**
     * 获取视频文件封面图
     * GET /api/front/files/{id}/cover
     */
    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> getFileCover(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(null);
        }
        if (!docFileService.canAccessFile(id, userId)) {
            return ResponseEntity.status(403).body(null);
        }
        byte[] cover = videoCoverService.generateCover(id);
        if (cover == null || cover.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                .body(cover);
    }

    @GetMapping("/fulltext-search")
    public Result<List<FileDTO>> fulltextSearchFiles(@RequestParam String keyword) {
        List<FileDTO> files = docFileService.fulltextSearchFiles(keyword);
        return Result.success(files);
    }

    @PutMapping("/{id}/transfer")
    public Result<FileDTO> transferFile(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body,
                                         Authentication authentication) {
        Long targetDirectoryId = body.get("targetDirectoryId") != null
                ? Long.valueOf(body.get("targetDirectoryId").toString()) : null;
        Long targetDepartmentId = body.get("targetDepartmentId") != null
                ? Long.valueOf(body.get("targetDepartmentId").toString()) : null;
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        try {
            FileDTO transferred = docFileService.transferFile(id, targetDirectoryId, targetDepartmentId, userId);
            // 审计日志：文件转移
            auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_TRANSFER,
                    "file", id, "转移文件到目录: " + targetDirectoryId, AuditLogService.STATUS_SUCCESS, userId, null);
            return Result.success(transferred);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/rename")
    public Result<FileDTO> renameFile(@PathVariable Long id,
                                       @RequestBody Map<String, String> body,
                                       Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        String newName = body.get("newName");
        if (newName == null || newName.trim().isEmpty()) {
            return Result.error("新文件名不能为空");
        }
        try {
            FileDTO renamed = docFileService.renameFile(id, newName.trim());
            // 审计日志：文件重命名
            auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_RENAME,
                    "file", id, "重命名文件为: " + newName.trim(), AuditLogService.STATUS_SUCCESS, userId, null);
            return Result.success(renamed);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/move")
    public Result<List<FileDTO>> moveFiles(@RequestBody Map<String, Object> body,
                                            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");

        @SuppressWarnings("unchecked")
        List<Long> fileIds = ((List<Number>) body.get("fileIds")).stream()
                .map(Number::longValue).toList();
        Long targetDirectoryId = body.get("targetDirectoryId") != null
                ? Long.valueOf(body.get("targetDirectoryId").toString()) : null;
        Integer targetSpaceType = body.get("targetSpaceType") != null
                ? Integer.valueOf(body.get("targetSpaceType").toString()) : null;
        Long targetSpaceId = body.get("targetSpaceId") != null
                ? Long.valueOf(body.get("targetSpaceId").toString()) : null;

        try {
            List<FileDTO> moved = docFileService.moveFiles(fileIds, targetDirectoryId, targetSpaceType, targetSpaceId, userId);
            // 审计日志：文件移动
            auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_MOVE,
                    "file", null, "移动文件: " + fileIds, AuditLogService.STATUS_SUCCESS, userId, null);
            return Result.success(moved);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/copy")
    public Result<List<FileDTO>> copyFiles(@RequestBody Map<String, Object> body,
                                            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");

        @SuppressWarnings("unchecked")
        List<Long> fileIds = ((List<Number>) body.get("fileIds")).stream()
                .map(Number::longValue).toList();
        Long targetDirectoryId = body.get("targetDirectoryId") != null
                ? Long.valueOf(body.get("targetDirectoryId").toString()) : null;
        Integer targetSpaceType = body.get("targetSpaceType") != null
                ? Integer.valueOf(body.get("targetSpaceType").toString()) : null;
        Long targetSpaceId = body.get("targetSpaceId") != null
                ? Long.valueOf(body.get("targetSpaceId").toString()) : null;

        try {
            List<FileDTO> copied = docFileService.copyFiles(fileIds, targetDirectoryId, targetSpaceType, targetSpaceId, userId);
            // 审计日志：文件复制
            auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_COPY,
                    "file", null, "复制文件: " + fileIds, AuditLogService.STATUS_SUCCESS, userId, null);
            return Result.success(copied);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
