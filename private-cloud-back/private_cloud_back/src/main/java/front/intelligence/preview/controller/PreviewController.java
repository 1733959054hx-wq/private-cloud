package front.intelligence.preview.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import cn.hutool.core.io.FileUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.opensagres.poi.xwpf.converter.xhtml.XHTMLConverter;
import fr.opensagres.poi.xwpf.converter.xhtml.XHTMLOptions;
import front.intelligence.preview.service.PreviewService;
import front.storage.service.StorageRouter;
import front.storage.service.StorageHelper;
import front.system.service.AuditLogService;
import front.hxconfig.AuthUtil;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.entity.DocShareLink;
import front.workspace.documentspace.repository.DocShareLinkRepository;
import front.workspace.documentspace.service.DocFileService;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.PicturesManager;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * 文件预览控制器
 * 
 * 架构设计：
 * - PDF：后端直接返回PDF文件流，前端使用pdf.js渲染（支持懒加载、文字选中）
 * - Word/PPT：后端通过LibreOffice转PDF后返回PDF流，前端pdf.js渲染
 * - Excel：使用EasyExcel流式解析（SAX），内存占用恒定
 * - 文本：服务端截断只读前512KB，避免大文件下载到前端
 * - 异步转换：Word/PPT转PDF采用异步线程池+状态轮询
 */
@RestController
@RequestMapping("/api/front/preview")
public class PreviewController {

    @Autowired
    private PreviewService previewService;

    @Autowired
    private DocFileService docFileService;

    @Autowired
    private DocShareLinkRepository shareLinkRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier("conversionExecutor")
    private ThreadPoolTaskExecutor conversionExecutor;

    @Autowired
    private front.storage.service.StorageRouter storageRouter;

    @Autowired
    private front.storage.service.StorageHelper storageHelper;

    @Autowired
    private front.workspace.documentspace.repository.DocFileVersionRepository fileVersionRepository;
    /** Redis Key 前缀：文档转换状态缓存 */
    private static final String PREVIEW_STATUS_PREFIX = "doc:preview:status:";
    /** Redis Key 前缀：文档转换分布式锁 */
    private static final String PREVIEW_LOCK_PREFIX = "doc:preview:lock:";

    /**
     * spring-boot-starter-webmvc (Spring Boot 4.x) 不再自动注册 ObjectMapper Bean，
     * 这里自行 new 一个给 Excel 缓存读写 JSON 使用；不影响 Spring MVC 自身的 JSON 响应。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private final Object libreOfficeLock = new Object();

    // ==================== 权限校验辅助 ====================

    /**
     * 统一预览权限校验：已登录用户检查文件访问权限，未登录用户检查是否有有效分享链接。
     * 分享链接预览场景下用户未登录，通过文件是否存在有效分享链接来放行。
     */
    private boolean checkPreviewAccess(Long fileId, Long userId) {
        // 已登录用户：检查文件访问权限
        if (userId != null && docFileService.canAccessFile(fileId, userId)) {
            return true;
        }
        // 未登录或无权限用户：检查该文件是否有有效的分享链接（允许访客预览）
        try {
            for (DocShareLink link : shareLinkRepository.findByFileIdAndStatus(fileId, 1)) {
                if (link.getExpireTime() != null && link.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
                    continue;
                }
                if (link.getMaxAccess() != null && link.getMaxAccess() > 0
                        && link.getAccessCount() != null && link.getAccessCount() >= link.getMaxAccess()) {
                    continue;
                }
                return true; // 存在有效的分享链接
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ==================== 原始文件流接口 ====================

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> previewFile(@PathVariable Long fileId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            return ResponseEntity.status(403).body(null);
        }
        // 审计日志：文件预览
        auditLogService.logAsync(AuditLogService.MODULE_FILE, AuditLogService.OP_VIEW,
                "file", fileId, "预览文件", AuditLogService.STATUS_SUCCESS, userId, null);
        String fileType = previewService.getFileType(fileId);
        Resource resource = previewService.getPreviewResource(fileId);

        MediaType contentType = determineContentType(fileType);

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFileName(fileId))
                .body(resource);
    }

    @GetMapping("/{fileId}/info")
    public ResponseEntity<DocFile> getFileInfo(@PathVariable Long fileId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            return ResponseEntity.status(403).body(null);
        }
        DocFile file = previewService.getFileInfo(fileId);
        return ResponseEntity.ok(file);
    }

    // ==================== PDF预览：直接返回PDF流 ====================

    /**
     * PDF文件直接返回原始流，前端使用pdf.js渲染
     * 支持Range请求以实现懒加载
     */
    @GetMapping(value = "/{fileId}/pdf-stream")
    public ResponseEntity<Resource> previewPdfStream(
            @PathVariable Long fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            return ResponseEntity.status(403).body(null);
        }
        try {
            DocFile file = previewService.getFileInfo(fileId);
            if (file == null) return ResponseEntity.notFound().build();

            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            String filePath = file.getFilePath();

            // MinIO 存储：优先返回预签名 URL 重定向
            if ("minio".equalsIgnoreCase(storageType)) {
                String presignedUrl = storageRouter.getPresignedUrl(storageType, filePath, java.time.Duration.ofMinutes(30));
                if (presignedUrl != null) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, presignedUrl)
                            .build();
                }
                // 预签名失败，降级为流式下载
                try (InputStream is = storageRouter.download(storageType, filePath)) {
                    byte[] bytes = is.readAllBytes();
                    Resource resource = new org.springframework.core.io.ByteArrayResource(bytes);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFileName(fileId))
                            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length))
                            .body(resource);
                }
            }

            // 本地存储：保持原有逻辑
            Path localPath = Path.of(filePath);
            if (!Files.exists(localPath)) return ResponseEntity.notFound().build();

            Resource resource = new UrlResource(localPath.toUri());
            long fileSize = Files.size(localPath);

            // 处理Range请求，支持pdf.js懒加载
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return buildRangeResponse(rangeHeader, fileSize, resource, fileId);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFileName(fileId))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== Word/PPT转PDF流（异步+轮询） ====================

    /**
     * 获取转换状态（从数据库持久化读取）
     */
    @GetMapping(value = "/{fileId}/convert-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getConvertStatus(@PathVariable Long fileId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            Map<String, Object> forbidden = new HashMap<>();
            forbidden.put("status", "FORBIDDEN");
            forbidden.put("message", "无权访问该文件");
            return ResponseEntity.status(403).body(forbidden);
        }
        Map<String, Object> result = new HashMap<>();

        // 1. 优先查 Redis 缓存（毫秒级返回）
        String statusKey = PREVIEW_STATUS_PREFIX + fileId;
        String cachedJson = stringRedisTemplate.opsForValue().get(statusKey);
        if (cachedJson != null) {
            try {
                Map<String, Object> cached = objectMapper.readValue(cachedJson, new TypeReference<Map<String, Object>>() {});
                // 缓存校验：若缓存为 COMPLETED，需确认数据库 previewPdfPath 仍然存在，
                // 否则说明缓存已过期（如上传新版本后 previewPdfPath 被重置为 null），
                // 需清除脏缓存，降级查数据库，避免前端白屏。
                if ("COMPLETED".equals(cached.get("status"))) {
                    DocFile dbFile = previewService.getFileInfo(fileId);
                    if (dbFile.getPreviewPdfPath() == null || !"COMPLETED".equals(dbFile.getPreviewStatus())) {
                        stringRedisTemplate.delete(statusKey);
                        // 不返回脏缓存，继续走数据库查询逻辑
                    } else {
                        return ResponseEntity.ok(cached);
                    }
                } else {
                    return ResponseEntity.ok(cached);
                }
            } catch (Exception ignored) { /* 缓存解析失败，降级查数据库 */ }
        }

        // 2. Redis 未命中，查数据库
        DocFile file = previewService.getFileInfo(fileId);
        String status = file.getPreviewStatus();
        if (status == null) status = "NOT_STARTED";

        result.put("status", status);

        // 校验物理文件是否真实存在，防止被意外删除导致前端白屏
        if ("COMPLETED".equals(status) && file.getPreviewPdfPath() != null) {
            String pdfStorageType = file.getStorageType() != null ? file.getStorageType() : "local";
            boolean pdfExists;
            if ("minio".equalsIgnoreCase(pdfStorageType)) {
                try {
                    pdfExists = storageRouter.exists(pdfStorageType, file.getPreviewPdfPath());
                } catch (Exception e) {
                    pdfExists = false;
                }
            } else {
                pdfExists = FileUtil.exist(file.getPreviewPdfPath());
            }
            if (pdfExists) {
                result.put("pdfUrl", "/api/front/preview/" + fileId + "/converted-pdf");
                // 回填 Redis 缓存（7天过期）
                cacheConvertStatus(fileId, result, 7);
            } else {
                // 文件意外丢失，重置状态，让前端重新触发
                previewService.updatePreviewStatus(fileId, "NOT_STARTED");
                result.put("status", "NOT_STARTED");
            }
        }

        // FAILED 状态时检查源文件是否存在，返回更具体的错误信息
        if ("FAILED".equals(status)) {
            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            boolean sourceExists;
            if ("minio".equalsIgnoreCase(storageType)) {
                try {
                    sourceExists = storageRouter.exists(storageType, file.getFilePath());
                } catch (Exception e) {
                    sourceExists = false;
                }
            } else {
                sourceExists = file.getFilePath() != null && Files.exists(Path.of(file.getFilePath()));
            }
            if (!sourceExists) {
                result.put("message", "源文件不存在，可能已被移动或删除");
            } else {
                result.put("message", "文档转换失败");
            }
            // 回填 Redis 缓存（1小时过期，失败状态短一些）
            cacheConvertStatus(fileId, result, 1);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 触发异步转换Word/PPT为PDF
     * 使用 Redis 分布式锁防止并发重复转换，Redis 缓存转换状态
     * @param force 是否强制重新转换（清除 Redis 缓存后重试，用于 FAILED 状态恢复）
     */
    @PostMapping(value = "/{fileId}/trigger-convert", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> triggerConvert(@PathVariable Long fileId,
                                                               @RequestParam(defaultValue = "false") boolean force,
                                                               Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            Map<String, Object> forbidden = new HashMap<>();
            forbidden.put("status", "FORBIDDEN");
            forbidden.put("message", "无权访问该文件");
            return ResponseEntity.status(403).body(forbidden);
        }
        Map<String, Object> result = new HashMap<>();
        DocFile file = previewService.getFileInfo(fileId);
        String statusKey = PREVIEW_STATUS_PREFIX + fileId;
        String lockKey = PREVIEW_LOCK_PREFIX + fileId;

        // 0. 强制重新转换：清除 Redis 缓存 + 重置数据库状态
        if (force) {
            stringRedisTemplate.delete(statusKey);
            if ("COMPLETED".equals(file.getPreviewStatus()) || "FAILED".equals(file.getPreviewStatus())) {
                previewService.updatePreviewStatus(fileId, "NOT_STARTED");
                file = previewService.getFileInfo(fileId); // 重新读取
            }
        }

        // lambda 中引用的变量必须是 effectively final，此处捕获最终状态
        final DocFile finalFile = file;

        // 1. 如果已完成且文件存在，直接返回（先查 Redis 缓存）
        if ("COMPLETED".equals(file.getPreviewStatus()) && file.getPreviewPdfPath() != null) {
            if (FileUtil.exist(file.getPreviewPdfPath())) {
                result.put("status", "COMPLETED");
                result.put("pdfUrl", "/api/front/preview/" + fileId + "/converted-pdf");
                return ResponseEntity.ok(result);
            }
            // PDF文件已丢失，重新转换
        }

        // 2. 如果正在处理中，直接返回（防止重复提交）
        if ("PROCESSING".equals(file.getPreviewStatus())) {
            result.put("status", "PROCESSING");
            result.put("message", "文档正在转换中，请稍候...");
            return ResponseEntity.ok(result);
        }

        // 3. 使用 Redis 分布式锁，防止并发重复转换
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);

        if (!Boolean.TRUE.equals(acquired)) {
            // 未获取到锁，说明另一个线程已在处理，返回处理中状态
            result.put("status", "PROCESSING");
            result.put("message", "排队转换中...");
            return ResponseEntity.ok(result);
        }

        try {
            // 4. 初始化状态为 PROCESSING，写入数据库 + Redis
            previewService.updatePreviewStatus(fileId, "PROCESSING");

            result.put("status", "PROCESSING");
            result.put("message", "文档转换已启动，请稍候...");
            cacheConvertStatus(fileId, result, 1); // PROCESSING 状态缓存1天

            // 5. 异步提交到线程池进行实际的物理转换
            conversionExecutor.execute(() -> {
                try {
                    String storageType = finalFile.getStorageType() != null ? finalFile.getStorageType() : "local";
                    String filePathStr = finalFile.getFilePath();

                    // 嗅探修复：若 filePath 实际是本地路径，强制按本地存储处理，
                    // 修复 storageType=minio 但 filePath 为本地路径的数据不一致问题。
                    if ("minio".equalsIgnoreCase(storageType) && looksLikeLocalPath(filePathStr)) {
                        System.err.println("[预览转换] 检测到 filePath 为本地路径但 storageType=minio, 自动修正为 local, fileId=" + fileId + ", filePath=" + filePathStr);
                        storageType = "local";
                    }

                    // 适配 MinIO：确保文件在本地可访问
                    Path filePath;
                    Path minioTempFile = null;
                    if ("minio".equalsIgnoreCase(storageType)) {
                        String fileName = filePathStr;
                        int lastSlash = fileName.lastIndexOf('/');
                        if (lastSlash >= 0) fileName = fileName.substring(lastSlash + 1);
                        int lastDot = fileName.lastIndexOf('.');
                        String suffix = lastDot >= 0 ? fileName.substring(lastDot) : ".tmp";
                        minioTempFile = storageHelper.ensureLocalAccessible(storageType, filePathStr, "convert_", suffix);
                        filePath = minioTempFile;
                    } else {
                        filePath = Path.of(filePathStr);
                    }

                    try {
                        if (!Files.exists(filePath)) {
                            System.err.println("[预览转换] 文件不存在: " + filePathStr + ", fileId=" + fileId);
                            String fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : null;
                            if (fileName != null && !"minio".equalsIgnoreCase(storageType)) {
                                for (long deptId = 0; deptId <= 10; deptId++) {
                                    Path candidate = Paths.get(uploadDir, "departments", String.valueOf(deptId), "files", fileName);
                                    if (Files.exists(candidate)) {
                                        System.err.println("[预览转换] 在备选路径找到文件: " + candidate + ", 自动修复filePath");
                                        try {
                                            Long correctDeptId = finalFile.getDepartmentId() != null ? finalFile.getDepartmentId() : 0L;
                                            Path targetPath = Paths.get(uploadDir, "departments", String.valueOf(correctDeptId), "files", fileName);
                                            if (!Files.exists(targetPath)) {
                                                Files.createDirectories(targetPath.getParent());
                                                Files.copy(candidate, targetPath);
                                            }
                                            previewService.updateFilePath(fileId, targetPath.toAbsolutePath().toString());
                                            filePath = targetPath;
                                            break;
                                        } catch (Exception fixEx) {
                                            System.err.println("[预览转换] 自动修复失败: " + fixEx.getMessage());
                                        }
                                    }
                                }
                            }
                            if (!Files.exists(filePath)) {
                                // 自愈逻辑：从版本记录中找一个文件存在的版本，自动修正 filePath
                                try {
                                    var versions = fileVersionRepository.findByFileIdOrderByVersionDesc(fileId);
                                    for (var ver : versions) {
                                        String verPath = ver.getFilePath();
                                        if (verPath == null || verPath.isEmpty()) continue;
                                        Path verAbsPath = Path.of(verPath);
                                        if (Files.exists(verAbsPath)) {
                                            System.err.println("[预览转换] 从版本记录自愈 filePath, fileId=" + fileId + ", version=" + ver.getVersion() + ", path=" + verAbsPath);
                                            previewService.updateFilePath(fileId, verAbsPath.toAbsolutePath().toString());
                                            filePath = verAbsPath;
                                            break;
                                        }
                                    }
                                } catch (Exception verEx) {
                                    System.err.println("[预览转换] 版本记录自愈失败, fileId=" + fileId + ", error=" + verEx.getMessage());
                                }
                            }
                            if (!Files.exists(filePath)) {
                                previewService.updatePreviewStatus(fileId, "FAILED");
                                cacheConvertStatus(fileId, Map.of("status", "FAILED", "message", "源文件不存在"), 1);
                                return;
                            }
                        }

                        Path tempPdf;
                        synchronized (libreOfficeLock) {
                            tempPdf = convertToPdfViaLibreOffice(filePath);
                        }

                        if (tempPdf != null && Files.exists(tempPdf)) {
                            File cacheDir = new File(uploadDir, "pdf_cache");
                            if (!cacheDir.exists()) {
                                cacheDir.mkdirs();
                            }

                            File persistPdf = new File(cacheDir, fileId + ".pdf");
                            FileUtil.copy(tempPdf.toFile(), persistPdf, true);

                            FileUtil.del(tempPdf.toFile());

                            // 若源文件在 MinIO，转换后的 PDF 也上传到 MinIO
                            String finalPdfPath;
                            if ("minio".equalsIgnoreCase(storageType)) {
                                String pdfObjectName = "pdf_cache/" + fileId + ".pdf";
                                try {
                                    StorageRouter.UploadResult uploadResult = storageRouter.upload(
                                            java.nio.file.Files.newInputStream(persistPdf.toPath()),
                                            pdfObjectName, persistPdf.getAbsolutePath(), persistPdf.length(), "application/pdf");
                                    finalPdfPath = uploadResult.getPath();
                                    // 删除本地临时 PDF
                                    FileUtil.del(persistPdf);
                                } catch (Exception e) {
                                    System.err.println("[预览转换] PDF 上传 MinIO 失败，保留本地: " + e.getMessage());
                                    finalPdfPath = persistPdf.getAbsolutePath();
                                }
                            } else {
                                finalPdfPath = persistPdf.getAbsolutePath();
                            }

                            previewService.updatePreviewSuccess(fileId, finalPdfPath);

                            // 转换成功，更新 Redis 缓存（7天过期）
                            Map<String, Object> successResult = new HashMap<>();
                            successResult.put("status", "COMPLETED");
                            successResult.put("pdfUrl", "/api/front/preview/" + fileId + "/converted-pdf");
                            cacheConvertStatus(fileId, successResult, 7);
                        } else {
                            System.err.println("[预览转换] LibreOffice转换失败, fileId=" + fileId + ", filePath=" + filePathStr);
                            previewService.updatePreviewStatus(fileId, "FAILED");
                            cacheConvertStatus(fileId, Map.of("status", "FAILED", "message", "文档转换失败"), 1);
                        }
                    } finally {
                        // 清理 MinIO 临时文件
                        if (minioTempFile != null) {
                            storageHelper.cleanupTempFile(minioTempFile);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[预览转换] 异常, fileId=" + fileId + ", error=" + e.getMessage());
                    e.printStackTrace();
                    previewService.updatePreviewStatus(fileId, "FAILED");
                    cacheConvertStatus(fileId, Map.of("status", "FAILED", "message", "转换异常: " + e.getMessage()), 1);
                } finally {
                    // 释放分布式锁
                    stringRedisTemplate.delete(lockKey);
                }
            });

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // 提交失败时释放锁
            stringRedisTemplate.delete(lockKey);
            throw e;
        }
    }

    /**
     * 获取已转换的PDF流（从数据库持久化路径读取）
     */
    /** 将转换状态缓存到 Redis（days 天后过期） */
    private void cacheConvertStatus(Long fileId, Map<String, Object> statusMap, int days) {
        try {
            String key = PREVIEW_STATUS_PREFIX + fileId;
            String json = objectMapper.writeValueAsString(statusMap);
            stringRedisTemplate.opsForValue().set(key, json, days, TimeUnit.DAYS);
        } catch (Exception e) {
            // 缓存写入失败不影响主功能
            System.err.println("[预览缓存] Redis写入失败, fileId=" + fileId + ", error=" + e.getMessage());
        }
    }

    @GetMapping(value = "/{fileId}/converted-pdf")
    public ResponseEntity<Resource> getConvertedPdf(
            @PathVariable Long fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            return ResponseEntity.status(403).body(null);
        }
        try {
            DocFile file = previewService.getFileInfo(fileId);
            // 严格校验数据库状态
            if (!"COMPLETED".equals(file.getPreviewStatus()) || file.getPreviewPdfPath() == null) {
                return ResponseEntity.notFound().build();
            }

            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            String pdfPath = file.getPreviewPdfPath();

            // 嗅探修复：若 pdfPath 实际是本地路径（含反斜杠/盘符/uploads），
            // 即使 storageType 标记为 minio，也按本地存储处理，避免预签名失败。
            if ("minio".equalsIgnoreCase(storageType) && looksLikeLocalPath(pdfPath)) {
                storageType = "local";
            }

            // MinIO 存储：优先返回预签名 URL 重定向
            if ("minio".equalsIgnoreCase(storageType)) {
                String presignedUrl = storageRouter.getPresignedUrl(storageType, pdfPath, java.time.Duration.ofMinutes(30));
                if (presignedUrl != null) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, presignedUrl)
                            .build();
                }
                // 预签名失败，降级为流式下载
                try (InputStream is = storageRouter.download(storageType, pdfPath)) {
                    byte[] bytes = is.readAllBytes();
                    Resource resource = new org.springframework.core.io.ByteArrayResource(bytes);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''converted.pdf")
                            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length))
                            .body(resource);
                }
            }

            // 本地存储：保持原有逻辑
            Path localPdfPath = Path.of(pdfPath);
            if (!Files.exists(localPdfPath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(localPdfPath.toUri());
            long fileSize = Files.size(localPdfPath);

            // 处理Range请求，支持pdf.js懒加载
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return buildRangeResponse(rangeHeader, fileSize, resource, fileId);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''converted.pdf")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== Word HTML预览（保留作为小文件快速预览） ====================

    @GetMapping(value = "/{fileId}/doc-html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewDocAsHtml(@PathVariable Long fileId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            return ResponseEntity.status(403).body(buildErrorHtml("无权访问", "您没有权限查看此文件"));
        }
        try {
            DocFile file = previewService.getFileInfo(fileId);
            if (file == null) return ResponseEntity.notFound().build();

            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            String filePath = file.getFilePath();

            // 大文件(>20MB)直接走PDF转换路径
            long fileSize = file.getFileSize() != null ? file.getFileSize() : 0L;
            if (fileSize > 20 * 1024 * 1024) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(buildRedirectToPdfHtml(fileId));
            }

            // 适配 MinIO：确保文件在本地可访问
            Path localPath = storageHelper.ensureLocalAccessible(storageType, filePath, "doc_preview_", ".doc");
            try {
                if (!Files.exists(localPath)) return ResponseEntity.notFound().build();
                try (InputStream is = Files.newInputStream(localPath);
                     HWPFDocument doc = new HWPFDocument(is)) {
                    String html = convertDocToHtml(doc);
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
                }
            } finally {
                if ("minio".equalsIgnoreCase(storageType)) {
                    storageHelper.cleanupTempFile(localPath);
                }
            }
        } catch (Exception e) {
            String errorHtml = buildErrorHtml("Word文档预览失败", "请下载后查看，或尝试PDF预览模式");
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(errorHtml);
        }
    }

    @GetMapping(value = "/{fileId}/docx-html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewDocxAsHtml(@PathVariable Long fileId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            return ResponseEntity.status(403).body(buildErrorHtml("无权访问", "您没有权限查看此文件"));
        }
        try {
            DocFile file = previewService.getFileInfo(fileId);
            if (file == null) return ResponseEntity.notFound().build();

            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            String filePath = file.getFilePath();

            // 大文件(>20MB)直接走PDF转换路径
            long fileSize = file.getFileSize() != null ? file.getFileSize() : 0L;
            if (fileSize > 20 * 1024 * 1024) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(buildRedirectToPdfHtml(fileId));
            }

            // 适配 MinIO：确保文件在本地可访问
            Path localPath = storageHelper.ensureLocalAccessible(storageType, filePath, "docx_preview_", ".docx");
            try {
                if (!Files.exists(localPath)) return ResponseEntity.notFound().build();
                try (InputStream is = Files.newInputStream(localPath);
                     XWPFDocument doc = new XWPFDocument(is)) {
                    String html = convertDocxToHtml(doc);
                    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
                }
            } finally {
                if ("minio".equalsIgnoreCase(storageType)) {
                    storageHelper.cleanupTempFile(localPath);
                }
            }
        } catch (Exception e) {
            String errorHtml = buildErrorHtml("Word文档预览失败", "请下载后查看，或尝试PDF预览模式");
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(errorHtml);
        }
    }

    // ==================== Excel预览：EasyExcel流式解析 + JSON 缓存 ====================

    /** 自定义异常：读满指定行数后提前中断，避免遍历整个文件 */
    static class RowLimitExceededException extends RuntimeException {
        RowLimitExceededException() { super("row limit reached"); }
    }

    @GetMapping(value = "/{fileId}/excel-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> previewExcelData(@PathVariable Long fileId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            Map<String, Object> forbidden = new HashMap<>();
            forbidden.put("error", "无权访问该文件");
            return ResponseEntity.status(403).body(forbidden);
        }
        try {
            DocFile file = previewService.getFileInfo(fileId);
            if (file == null) return ResponseEntity.notFound().build();

            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            String filePath = file.getFilePath();

            // 适配 MinIO：确保文件在本地可访问
            Path localPath = storageHelper.ensureLocalAccessible(storageType, filePath, "excel_preview_", ".xlsx");
            try {
                if (!Files.exists(localPath)) return ResponseEntity.notFound().build();

                // 1. 尝试读取缓存（源文件 size+mtime 校验，命中后毫秒级返回）
                Map<String, Object> cached = tryReadExcelCache(fileId, localPath);
                if (cached != null) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                            .body(cached);
                }

                // 2. 缓存未命中或失效，解析 Excel
                Map<String, Object> result = parseExcelData(localPath);
                if (result.containsKey("error")) {
                    return ResponseEntity.ok(result);
                }

                // 3. 写入缓存
                writeExcelCache(fileId, localPath, result);

                return ResponseEntity.ok(result);
            } finally {
                if ("minio".equalsIgnoreCase(storageType)) {
                    storageHelper.cleanupTempFile(localPath);
                }
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Excel文件解析失败: " + e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    /** 解析 Excel 数据（不含缓存逻辑） */
    private Map<String, Object> parseExcelData(Path filePath) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> sheets = new ArrayList<>();

        int maxRowsPerSheet = 100;
        int maxColsPerSheet = 50;

        // EasyExcel 4.x 不再提供 ExcelReader/ExcelReaderBuilder
        // 使用两步策略：先用POI读取所有sheet名称，再逐sheet读取数据
        // 第一步：用 POI WorkbookFactory 一次性拿到所有 sheet 名称（兼容 .xls / .xlsx）
        List<String> sheetNames = new ArrayList<>();
        try (org.apache.poi.ss.usermodel.Workbook wb =
                     org.apache.poi.ss.usermodel.WorkbookFactory.create(filePath.toFile())) {
            int count = wb.getNumberOfSheets();
            for (int s = 0; s < count; s++) {
                String name = wb.getSheetName(s);
                sheetNames.add(name == null || name.isEmpty() ? ("Sheet" + (s + 1)) : name);
            }
        } catch (Exception e) {
            // 探测失败，使用默认sheet
            sheetNames.add("Sheet1");
        }

        // 第二步：逐sheet读取数据（最多100行）
        for (int s = 0; s < sheetNames.size(); s++) {
            Map<String, Object> sheetData = new HashMap<>();
            sheetData.put("name", sheetNames.get(s));

            List<List<String>> rows = new ArrayList<>();
            int[] rowCount = {0};
            int[] totalRowCount = {0};

            try {
                EasyExcel.read(filePath.toFile())
                        .sheet(s)
                        .headRowNumber(0)
                        .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                            @Override
                            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                                totalRowCount[0]++;
                                if (rowCount[0] < maxRowsPerSheet) {
                                    List<String> cells = new ArrayList<>();
                                    for (int c = 0; c < maxColsPerSheet; c++) {
                                        cells.add(data.getOrDefault(c, ""));
                                    }
                                    rows.add(cells);
                                    rowCount[0]++;
                                } else {
                                    // 读满100行后抛异常中断SAX解析，不再遍历剩余行
                                    throw new RowLimitExceededException();
                                }
                            }

                            @Override
                            public void doAfterAllAnalysed(AnalysisContext context) {
                                // 读取完毕
                            }
                        }).doRead();
            } catch (RowLimitExceededException e) {
                // 正常中断，继续处理下一个sheet
            }

            // 如果提前中断，标记为近似值，前端显示"100+行"
            if (rowCount[0] >= maxRowsPerSheet) {
                sheetData.put("totalRowsApprox", true);
            }

            sheetData.put("totalRows", Math.max(totalRowCount[0], rowCount[0]));
            sheetData.put("rows", rows);
            sheets.add(sheetData);
        }

        result.put("sheets", sheets);
        return result;
    }

    /** 尝试读取 Excel 解析缓存。命中且源文件未变更则返回，反之返回 null */
    private Map<String, Object> tryReadExcelCache(Long fileId, Path filePath) {
        try {
            File cacheFile = new File(uploadDir, "excel_cache/" + fileId + ".json");
            if (!cacheFile.exists()) return null;

            long currentSize = Files.size(filePath);
            long currentMtime = Files.getLastModifiedTime(filePath).toMillis();

            Map<String, Object> cache = objectMapper.readValue(cacheFile, new TypeReference<Map<String, Object>>() {});
            Object sizeObj = cache.remove("_sourceSize");
            Object mtimeObj = cache.remove("_sourceMtime");
            if (sizeObj == null || mtimeObj == null) return null;

            long cachedSize = ((Number) sizeObj).longValue();
            long cachedMtime = ((Number) mtimeObj).longValue();
            if (cachedSize == currentSize && cachedMtime == currentMtime) {
                return cache;
            }
        } catch (Exception e) {
            // 缓存读取失败，忽略，走原解析
        }
        return null;
    }

    /** 写入 Excel 解析缓存（写失败不影响主功能） */
    private void writeExcelCache(Long fileId, Path filePath, Map<String, Object> result) {
        try {
            File cacheDir = new File(uploadDir, "excel_cache");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) return;
            File cacheFile = new File(cacheDir, fileId + ".json");

            // 在写入时附带源文件 size+mtime 用于后续校验
            result.put("_sourceSize", Files.size(filePath));
            result.put("_sourceMtime", Files.getLastModifiedTime(filePath).toMillis());
            objectMapper.writeValue(cacheFile, result);
        } catch (Exception e) {
            // 缓存写入失败不影响主功能
        }
    }

    // ==================== 文本预览：服务端截断 ====================

    @GetMapping(value = "/{fileId}/text-preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> previewTextFile(@PathVariable Long fileId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        // 权限校验：未登录或无权访问文件则返回 403
        if (!checkPreviewAccess(fileId, userId)) {
            Map<String, Object> forbidden = new HashMap<>();
            forbidden.put("error", "无权访问该文件");
            return ResponseEntity.status(403).body(forbidden);
        }
        try {
            DocFile file = previewService.getFileInfo(fileId);
            if (file == null) return ResponseEntity.notFound().build();

            String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
            String filePathStr = file.getFilePath();

            long fileSize;
            int maxBytes = 512 * 1024; // 512KB
            byte[] bytes;

            if ("minio".equalsIgnoreCase(storageType)) {
                // MinIO 存储：下载文件并截取前 512KB
                try (InputStream is = storageRouter.download(storageType, filePathStr)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1 && bos.size() < maxBytes) {
                        int remaining = maxBytes - bos.size();
                        if (n > remaining) n = remaining;
                        bos.write(buffer, 0, n);
                    }
                    bytes = bos.toByteArray();
                    // 文件大小从 DocFile 实体获取
                    fileSize = file.getFileSize() != null ? file.getFileSize() : bytes.length;
                }
            } else {
                // 本地存储：保持原有逻辑
                Path filePath = Path.of(filePathStr);
                if (!Files.exists(filePath)) return ResponseEntity.notFound().build();

                fileSize = Files.size(filePath);
                // 使用RandomAccessFile精确控制读取，避免大文件占用过多内存
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
                    int readLen = (int) Math.min(fileSize, maxBytes);
                    bytes = new byte[readLen];
                    raf.readFully(bytes, 0, readLen);
                }
            }

            String content = new String(bytes, StandardCharsets.UTF_8);
            boolean truncated = fileSize > maxBytes;

            Map<String, Object> result = new HashMap<>();
            result.put("content", content);
            result.put("truncated", truncated);
            result.put("fileSize", fileSize);
            result.put("previewSize", Math.min(fileSize, maxBytes));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "文本文件读取失败: " + e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    // ==================== 私有方法 ====================

    private String convertDocToHtml(HWPFDocument doc) {
        try {
            Document newDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            WordToHtmlConverter converter = new WordToHtmlConverter(newDocument);

            converter.setPicturesManager((content, pictureType, suggestedName, width, height) -> {
                String base64 = Base64.getEncoder().encodeToString(content);
                String mimeType = pictureType == null ? "image/png" : pictureType.getMime();
                return "data:" + mimeType + ";base64," + base64;
            });

            converter.processDocument(doc);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DOMSource domSource = new DOMSource(converter.getDocument());
            javax.xml.transform.Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "html");
            transformer.transform(domSource, new StreamResult(out));

            String html = out.toString(StandardCharsets.UTF_8);

            StringBuilder css = new StringBuilder();
            css.append("<style>");
            css.append("body{font-family:'SimSun','Microsoft YaHei',sans-serif;margin:0 auto;padding:40px 60px;line-height:1.5;color:#000;background:#fff;}");
            css.append("@page{size:A4;margin:2.54cm;}");
            css.append("div{word-wrap:break-word;overflow-wrap:break-word;}");
            css.append("p{margin:0 0 6pt 0;text-indent:0;}");
            css.append("h1{font-size:22pt;font-weight:bold;margin:12pt 0 6pt 0;}");
            css.append("h2{font-size:16pt;font-weight:bold;margin:10pt 0 6pt 0;}");
            css.append("h3{font-size:14pt;font-weight:bold;margin:8pt 0 4pt 0;}");
            css.append("h4{font-size:12pt;font-weight:bold;margin:6pt 0 4pt 0;}");
            css.append("img{max-width:100%;height:auto;display:block;margin:6pt auto;}");
            css.append("table{border-collapse:collapse;width:auto;margin:6pt 0;}");
            css.append("td,th{border:1px solid #000;padding:4pt 6pt;vertical-align:top;word-wrap:break-word;}");
            css.append("th{background:#f0f0f0;font-weight:bold;}");
            css.append("ul,ol{margin:6pt 0;padding-left:24pt;}");
            css.append("li{margin:2pt 0;}");
            css.append("a{color:#0563c1;text-decoration:underline;}");
            css.append("span{white-space:pre-wrap;}");
            css.append("</style>");
            html = html.replace("</head>", css + "</head>");

            return html;
        } catch (Exception e) {
            return convertDocToTextHtml(doc);
        }
    }

    private String convertDocxToHtml(XWPFDocument doc) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            XHTMLOptions options = XHTMLOptions.create();
            options.setImageManager(new fr.opensagres.poi.xwpf.converter.xhtml.Base64EmbedImgManager());

            XHTMLConverter.getInstance().convert(doc, out, options);

            String html = out.toString(StandardCharsets.UTF_8);

            StringBuilder css = new StringBuilder();
            css.append("<style>");
            css.append("body{font-family:'SimSun','Microsoft YaHei',sans-serif;margin:0 auto;padding:40px 60px;line-height:1.5;color:#000;background:#fff;}");
            css.append("@page{size:A4;margin:2.54cm;}");
            css.append("div{word-wrap:break-word;overflow-wrap:break-word;}");
            css.append("p{margin:0 0 6pt 0;text-indent:0;}");
            css.append("h1{font-size:22pt;font-weight:bold;margin:12pt 0 6pt 0;}");
            css.append("h2{font-size:16pt;font-weight:bold;margin:10pt 0 6pt 0;}");
            css.append("h3{font-size:14pt;font-weight:bold;margin:8pt 0 4pt 0;}");
            css.append("h4{font-size:12pt;font-weight:bold;margin:6pt 0 4pt 0;}");
            css.append("img{max-width:100%;height:auto;display:block;margin:6pt auto;}");
            css.append("table{border-collapse:collapse;width:auto;margin:6pt 0;}");
            css.append("td,th{border:1px solid #000;padding:4pt 6pt;vertical-align:top;word-wrap:break-word;}");
            css.append("th{background:#f0f0f0;font-weight:bold;}");
            css.append("ul,ol{margin:6pt 0;padding-left:24pt;}");
            css.append("li{margin:2pt 0;}");
            css.append("a{color:#0563c1;text-decoration:underline;}");
            css.append("span{white-space:pre-wrap;}");
            css.append("</style>");
            html = html.replace("</head>", css + "</head>");

            return html;
        } catch (Exception e) {
            return buildErrorHtml("DOCX文档转换失败", "请下载后查看，或尝试PDF预览模式");
        }
    }

    private String convertDocToTextHtml(HWPFDocument doc) {
        try {
            WordExtractor extractor = new WordExtractor(doc);
            String text = extractor.getText();
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><meta charset='UTF-8'>");
            sb.append("<style>body{font-family:'SimSun','Microsoft YaHei',sans-serif;margin:0 auto;padding:40px 60px;line-height:1.5;color:#000;background:#fff;}");
            sb.append("p{margin:0 0 6pt 0;white-space:pre-wrap;}</style></head><body>");
            for (String paragraph : text.split("\\n")) {
                String escaped = paragraph.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                sb.append("<p>").append(escaped).append("</p>");
            }
            sb.append("</body></html>");
            return sb.toString();
        } catch (Exception ex) {
            return buildErrorHtml("Word文档内容提取失败", "请下载后查看");
        }
    }

    /**
     * 构建重定向到PDF预览的HTML（用于大文件Word/PPT）
     */
    private String buildRedirectToPdfHtml(Long fileId) {
        return "<html><head><meta charset='UTF-8'>" +
                "<style>body{display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;color:#409EFF;}" +
                ".container{text-align:center;}.icon{font-size:48px;margin-bottom:16px;}</style></head>" +
                "<body><div class='container'>" +
                "<div class='icon'>📄</div>" +
                "<h3>文件较大，已自动切换为PDF预览模式</h3>" +
                "<p>正在转换中，请稍候...</p>" +
                "</div></body></html>";
    }

    /**
     * 调用LibreOffice将Office文档转换为PDF
     * 超时时间300秒，支持大文件转换
     */
    private Path convertToPdfViaLibreOffice(Path sourceFile) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            String sofficeCmd;

            if (osName.contains("win")) {
                String[] winPaths = {
                    "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                    "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"
                };
                sofficeCmd = null;
                for (String p : winPaths) {
                    if (Files.exists(Path.of(p))) {
                        sofficeCmd = p;
                        break;
                    }
                }
                if (sofficeCmd == null) {
                    sofficeCmd = "soffice";
                }
            } else {
                sofficeCmd = "soffice";
            }

            Path tempDir = Files.createTempDirectory("office2pdf_");

            ProcessBuilder pb = new ProcessBuilder(
                sofficeCmd,
                "--headless",
                "--convert-to", "pdf",
                "--outdir", tempDir.toString(),
                sourceFile.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String processOutput;
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                processOutput = sb.toString();
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.err.println("[预览转换] LibreOffice超时, sourceFile=" + sourceFile);
                return null;
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                System.err.println("[预览转换] LibreOffice退出码=" + exitCode + ", sourceFile=" + sourceFile + ", output=" + processOutput);
                return null;
            }

            // 找到生成的PDF文件
            String sourceName = sourceFile.getFileName().toString();
            String baseName = sourceName.contains(".")
                ? sourceName.substring(0, sourceName.lastIndexOf("."))
                : sourceName;
            Path pdfPath = tempDir.resolve(baseName + ".pdf");

            if (!Files.exists(pdfPath)) {
                try (java.util.stream.Stream<Path> paths = Files.list(tempDir)) {
                    Optional<Path> found = paths.filter(p -> p.toString().endsWith(".pdf")).findFirst();
                    if (found.isPresent()) {
                        pdfPath = found.get();
                    } else {
                        return null;
                    }
                }
            }

            // 将PDF复制到新的临时位置，然后删除临时目录
            Path resultPdf = Files.createTempFile("office_preview_", ".pdf");
            Files.copy(pdfPath, resultPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            try (java.util.stream.Stream<Path> paths = Files.list(tempDir)) {
                paths.forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
            try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}

            return resultPdf;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建Range请求的响应（支持pdf.js懒加载）
     * 解析 Range: bytes=start-end 头，返回206 Partial Content
     */
    private ResponseEntity<Resource> buildRangeResponse(String rangeHeader, long fileSize, Resource resource, Long fileId) {
        try {
            String byteRange = rangeHeader.substring(6); // 去掉 "bytes="
            String[] ranges = byteRange.split("-");
            long start = Long.parseLong(ranges[0].trim());
            long end = ranges.length > 1 && !ranges[1].trim().isEmpty()
                    ? Long.parseLong(ranges[1].trim())
                    : fileSize - 1;

            // 边界检查
            if (start >= fileSize || end >= fileSize || start > end) {
                return ResponseEntity.status(416) // Range Not Satisfiable
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                        .build();
            }

            long contentLength = end - start + 1;

            // 使用PartialResource包装，只返回请求范围内的数据
            org.springframework.core.io.AbstractResource partialResource = new org.springframework.core.io.AbstractResource() {
                @Override
                public java.io.InputStream getInputStream() throws java.io.IOException {
                    java.io.InputStream is = resource.getInputStream();
                    is.skip(start);
                    return new java.io.FilterInputStream(is) {
                        long remaining = contentLength;
                        @Override
                        public int read() throws java.io.IOException {
                            if (remaining <= 0) return -1;
                            int b = super.read();
                            if (b != -1) remaining--;
                            return b;
                        }
                        @Override
                        public int read(byte[] b, int off, int len) throws java.io.IOException {
                            if (remaining <= 0) return -1;
                            int toRead = (int) Math.min(len, remaining);
                            int read = super.read(b, off, toRead);
                            if (read > 0) remaining -= read;
                            return read;
                        }
                    };
                }
                @Override
                public String getDescription() {
                    return "Partial content of file " + fileId;
                }
                @Override
                public long contentLength() {
                    return contentLength;
                }
            };

            return ResponseEntity.status(206) // Partial Content
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFileName(fileId))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(partialResource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String buildErrorHtml(String title, String message) {
        return "<html><body style='display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;color:#909399;'>" +
                "<div style='text-align:center;'><h3>" + title + "</h3><p>" + message + "</p></div></body></html>";
    }

    private String encodeFileName(Long fileId) {
        try {
            DocFile file = previewService.getFileInfo(fileId);
            return java.net.URLEncoder.encode(file.getFileName(), "UTF-8");
        } catch (Exception e) {
            return "file";
        }
    }

    private MediaType determineContentType(String fileType) {
        if (fileType == null) return MediaType.APPLICATION_OCTET_STREAM;
        return switch (fileType.toLowerCase()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "pptx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "ppt" -> MediaType.parseMediaType("application/vnd.ms-powerpoint");
            case "xlsx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "mp4" -> MediaType.parseMediaType("video/mp4");
            case "mp3" -> MediaType.parseMediaType("audio/mpeg");
            case "wav" -> MediaType.parseMediaType("audio/wav");
            case "avi" -> MediaType.parseMediaType("video/x-msvideo");
            case "mov" -> MediaType.parseMediaType("video/quicktime");
            case "webm" -> MediaType.parseMediaType("video/webm");
            case "ogg" -> MediaType.parseMediaType("audio/ogg");
            case "mkv" -> MediaType.parseMediaType("video/x-matroska");
            case "flv" -> MediaType.parseMediaType("video/x-flv");
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            case "ico" -> MediaType.parseMediaType("image/x-icon");
            case "tiff", "tif" -> MediaType.parseMediaType("image/tiff");
            case "txt", "log", "cfg", "conf", "ini", "properties" -> MediaType.TEXT_PLAIN;
            case "md" -> MediaType.TEXT_PLAIN;
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "json" -> MediaType.APPLICATION_JSON;
            case "xml" -> MediaType.APPLICATION_XML;
            case "html", "htm" -> MediaType.TEXT_HTML;
            case "css" -> MediaType.parseMediaType("text/css");
            case "js" -> MediaType.parseMediaType("text/javascript");
            case "java", "py", "c", "cpp", "h", "go", "rs", "ts", "sql", "yaml", "yml", "sh", "bat" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * 判断路径是否为本地文件系统路径（与 StorageHelper.looksLikeLocalPath 逻辑一致）。
     * 用于修复 storageType 与实际路径不一致的历史数据。
     */
    private boolean looksLikeLocalPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        if (filePath.indexOf('\\') >= 0) return true;
        if (filePath.startsWith("./") || filePath.startsWith(".\\")) return true;
        if (filePath.length() >= 2 && filePath.charAt(1) == ':') return true;
        if (filePath.contains("uploads") || filePath.contains("departments") || filePath.contains("pdf_cache")) return true;
        return false;
    }
}
