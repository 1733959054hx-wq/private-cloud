package front.workflow.controller;

import front.esign.service.OfficeConverter;
import front.esign.service.StampService;
import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.storage.service.StorageRouter;
import front.system.entity.SysUser;
import front.system.service.SysUserService;
import front.workspace.documentspace.repository.DocDirectoryRepository;
import front.workflow.entity.ApprovalRequest;
import front.workflow.repository.ApprovalRequestRepository;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审批工作流控制器
 */
@RestController
@RequestMapping("/api/workflow/approval")
public class ApprovalController {

    private static final Set<String> CONVERTIBLE_EXTS = Set.of(
            "doc", "docx", "docm", "xls", "xlsx", "xlsm", "xlsb", "csv",
            "ppt", "pptx", "pptm", "odt", "ods", "odp", "rtf", "txt", "md", "pdf"
    );

    @Autowired
    private DocFileRepository docFileRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private OfficeConverter officeConverter;

    @Autowired
    private StampService stampService;

    @Autowired
    private DocDirectoryRepository directoryRepository;

    @Autowired
    private StorageRouter storageRouter;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    // ==================== 可转换文件 ====================

    @GetMapping("/convertible-files")
    public Result<List<Map<String, Object>>> getConvertibleFiles(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录");
        List<DocFile> files = docFileRepository.findByDeletedAndStatus(0, 1);
        List<Map<String, Object>> result = files.stream()
                .filter(f -> f.getFileType() != null && CONVERTIBLE_EXTS.contains(f.getFileType().toLowerCase()))
                .filter(f -> f.getFileName() == null || !f.getFileName().contains("_已签章"))
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("fileName", f.getFileName());
                    m.put("fileType", f.getFileType());
                    m.put("fileSize", f.getFileSize());
                    m.put("uploaderName", f.getUploaderName());
                    m.put("createTime", f.getCreateTime());
                    return m;
                }).collect(Collectors.toList());
        return Result.success(result);
    }

    // ==================== 提交审批 ====================

    @PostMapping("/submit")
    public Result<Map<String, Object>> submitApproval(Authentication authentication,
                                                       @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录");
        Long documentId = body.get("documentId") != null ? Long.valueOf(body.get("documentId").toString()) : null;
        String title = body.get("title") != null ? body.get("title").toString() : "";
        if (documentId == null) return Result.error("请选择文档");
        DocFile doc = docFileRepository.findById(documentId).orElse(null);
        if (doc == null) return Result.error("文档不存在");
        String ext = doc.getFileType();
        if (ext == null || !CONVERTIBLE_EXTS.contains(ext.toLowerCase()))
            return Result.error("该文档类型不支持转换为PDF");
        ApprovalRequest req = new ApprovalRequest();
        req.setApplicantId(userId);
        req.setDocumentId(documentId);
        req.setTitle(title);
        req.setType("stamp");
        req.setStatus(0);
        req.setCurrentStep(0);
        approvalRequestRepository.save(req);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", req.getId());
        r.put("status", req.getStatus());
        r.put("message", "审批请求已提交");
        return Result.success(r);
    }

    // ==================== 我的审批记录 ====================

    @GetMapping("/my-requests")
    public Result<List<Map<String, Object>>> getMyRequests(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录");
        List<ApprovalRequest> list = approvalRequestRepository.findByApplicantIdOrderByCreateTimeDesc(userId);
        List<Map<String, Object>> result = list.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("documentId", r.getDocumentId());
            m.put("title", r.getTitle());
            m.put("type", r.getType());
            m.put("status", r.getStatus());
            m.put("createTime", r.getCreateTime());
            String fn = docFileRepository.findById(r.getDocumentId())
                    .map(DocFile::getFileName).orElse(null);
            m.put("fileName", fn);
            // 盖章后的文件ID和名称（如果已通过）
            Long sfId = r.getStampedFileId();
            m.put("stampedFileId", sfId);
            if (sfId != null) {
                String sfn = docFileRepository.findById(sfId)
                        .map(DocFile::getFileName).orElse(null);
                m.put("stampedFileName", sfn);
            } else {
                m.put("stampedFileName", null);
            }
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    // ==================== 审批通过 + 自动盖章 ====================

    /**
     * 审批通过，自动转换为PDF → 盖章 → 保存为新文件到指定目录
     * POST /api/workflow/approval/approve/{id}?directoryId=xxx&signer=张三
     */
    @PostMapping("/approve/{id}")
    public Result<Map<String, Object>> approveAndStamp(Authentication authentication,
                                                        @PathVariable Long id,
                                                        @RequestParam Long directoryId,
                                                        @RequestParam(defaultValue = "系统管理员") String signer) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录");

        ApprovalRequest request = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("审批请求不存在"));
        if (request.getStatus() != 0) return Result.error("审批已处理");

        DocFile sourceDoc = docFileRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new RuntimeException("原文件不存在"));

        SysUser user = sysUserService.findById(userId);
        Long deptId = user != null && user.getDepartmentId() != null ? user.getDepartmentId() : 0L;

        try {
            // 1. 解析源文件路径（适配 MinIO）
            String sourceStorageType = sourceDoc.getStorageType() != null ? sourceDoc.getStorageType() : "local";
            File sourceFile;
            Path minioTempSource = null;

            if ("minio".equalsIgnoreCase(sourceStorageType)) {
                // MinIO 文件下载到本地临时文件
                String srcExt = sourceDoc.getFileType() != null ? sourceDoc.getFileType() : "tmp";
                minioTempSource = Files.createTempFile("approval_source_", "." + srcExt);
                try (InputStream is = storageRouter.download(sourceStorageType, sourceDoc.getFilePath())) {
                    Files.copy(is, minioTempSource, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                sourceFile = minioTempSource.toFile();
            } else {
                sourceFile = resolveFile(sourceDoc.getFilePath());
            }

            if (sourceFile == null || !sourceFile.exists()) {
                return Result.error("源文件不存在: " + sourceDoc.getFilePath());
            }
            String sourcePath = sourceFile.getAbsolutePath();

            // 2. 转换为 PDF
            String pdfPath;
            boolean isPdf = sourcePath.toLowerCase().endsWith(".pdf");
            if (isPdf) {
                pdfPath = sourcePath;
            } else {
                pdfPath = sourcePath.substring(0, sourcePath.lastIndexOf('.')) + ".pdf";
                officeConverter.convertToPdf(sourcePath, pdfPath);
            }

            // 3. 盖章
            byte[] pdfBytes = Files.readAllBytes(Paths.get(pdfPath));
            byte[] stampedPdf;
            try (InputStream is = new ByteArrayInputStream(pdfBytes)) {
                stampedPdf = stampService.stampPdf(is, signer);
            }

            // 4. 保存盖章后的 PDF（根据源文件存储类型决定存储位置）
            String storedName = "stamped_" + sourceDoc.getId() + "_" + System.currentTimeMillis() + ".pdf";
            String finalFilePath;
            String finalStorageType;

            if ("minio".equalsIgnoreCase(sourceStorageType)) {
                // MinIO 存储：上传到 MinIO
                String objectName = "departments/" + deptId + "/files/" + storedName;
                try (InputStream is = new ByteArrayInputStream(stampedPdf)) {
                    StorageRouter.UploadResult uploadResult = storageRouter.upload(
                            is, objectName, null, stampedPdf.length, "application/pdf");
                    finalFilePath = uploadResult.getPath();
                    finalStorageType = uploadResult.getStorageType();
                }
            } else {
                // 本地存储
                Path finalPath = Paths.get(uploadDir, "departments", String.valueOf(deptId), "files", storedName);
                Files.createDirectories(finalPath.getParent());
                Files.write(finalPath, stampedPdf);
                finalFilePath = finalPath.toString();
                finalStorageType = "local";
            }

            // 5. 创建 DocFile 记录
            DocFile newFile = new DocFile();
            String baseName = sourceDoc.getFileName();
            int dot = baseName.lastIndexOf('.');
            String nameWithoutExt = dot > 0 ? baseName.substring(0, dot) : baseName;
            newFile.setFileName(nameWithoutExt + "_已签章.pdf");
            newFile.setFileType("pdf");
            newFile.setFileSize((long) stampedPdf.length);
            newFile.setFilePath(finalFilePath);
            newFile.setStorageType(finalStorageType);
            newFile.setMd5(sourceDoc.getMd5());
            newFile.setDirectoryId(directoryId);
            newFile.setDepartmentId(deptId);
            // 从目标目录获取空间权限，确保文件在正确空间可见
            directoryRepository.findById(directoryId).ifPresent(dir -> {
                newFile.setSpaceType(dir.getSpaceType());
                newFile.setSpaceId(dir.getSpaceId());
            });
            newFile.setUploaderId(userId);
            newFile.setUploaderName(user != null ? (user.getRealName() != null ? user.getRealName() : user.getUsername()) : "系统");
            newFile.setVersion(1);
            newFile.setStatus(1);
            newFile.setDeleted(0);
            docFileRepository.save(newFile);

            // 6. 更新审批状态
            request.setStatus(2); // 已通过
            request.setStampedFileId(newFile.getId());
            approvalRequestRepository.save(request);

            // 7. 清理临时文件
            if (!isPdf) {
                try { Files.deleteIfExists(Paths.get(pdfPath)); } catch (Exception ignored) {}
            }
            if (minioTempSource != null) {
                try { Files.deleteIfExists(minioTempSource); } catch (Exception ignored) {}
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("approvalId", request.getId());
            result.put("stampedFileId", newFile.getId());
            result.put("stampedFileName", newFile.getFileName());
            result.put("message", "审批通过，签章完成");
            return Result.success(result);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("签章失败: " + e.getMessage());
        }
    }

    /**
     * 获取盖章后文件的信息
     */
    @GetMapping("/stamped-file/{fileId}")
    public Result<Map<String, Object>> getStampedFileInfo(@PathVariable Long fileId) {
        DocFile file = docFileRepository.findById(fileId).orElse(null);
        if (file == null) return Result.error("文件不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", file.getId());
        m.put("fileName", file.getFileName());
        m.put("fileType", file.getFileType());
        m.put("fileSize", file.getFileSize());
        m.put("createTime", file.getCreateTime());
        return Result.success(m);
    }

    // ==================== 文件路径解析 ====================

    private File resolveFile(String filePath) {
        if (filePath == null) return null;
        File f = new File(filePath);
        if (f.exists()) return f;
        f = new File(System.getProperty("user.dir"), filePath);
        if (f.exists()) return f;
        String clean = filePath.replaceAll("^[\\\\/.]*uploads?[\\\\/]", "");
        f = new File(uploadDir, clean);
        if (f.exists()) return f;
        return null;
    }
}
