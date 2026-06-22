package front.search.engine.controller;

import front.esign.service.OfficeConverter;
import front.storage.service.StorageRouter;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档内容加载/保存接口（协同编辑器用）
 * 加载：LibreOffice 转 HTML → 加载到 Quill
 * 保存：Quill HTML → LibreOffice 转回原格式
 */
@RestController
@RequestMapping("/api/document")
public class DocContentController {

    @Resource
    private OfficeConverter officeConverter;

    @Resource
    private DocFileRepository fileRepository;

    @Resource
    private StorageRouter storageRouter;

    @GetMapping("/{id}/content")
    public ResponseEntity<Map<String, Object>> getContent(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            DocFile docFile = fileRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("文档不存在"));

            String storageType = docFile.getStorageType() != null ? docFile.getStorageType() : "local";
            String content;
            java.nio.file.Path minioTemp = null;

            if ("minio".equalsIgnoreCase(storageType)) {
                // MinIO：下载到临时文件
                String ext = docFile.getFileType() != null ? docFile.getFileType() : "tmp";
                minioTemp = Files.createTempFile("doc_content_", "." + ext);
                try (InputStream is = storageRouter.download(storageType, docFile.getFilePath())) {
                    Files.copy(is, minioTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                content = officeConverter.extractText(minioTemp.toString());
            } else {
                // 本地存储
                String filePath = getActualFilePath(docFile);
                if (filePath == null || !Files.exists(Paths.get(filePath))) {
                    result.put("code", 404);
                    result.put("msg", "文件不存在于存储路径");
                    return ResponseEntity.status(404).body(result);
                }
                content = officeConverter.extractText(filePath);
            }

            result.put("code", 200);
            Map<String, Object> data = new HashMap<>();
            data.put("content", content);
            data.put("fileName", docFile.getFileName());
            data.put("fileType", docFile.getFileType());
            result.put("data", data);

            // 清理临时文件
            if (minioTemp != null) {
                try { Files.deleteIfExists(minioTemp); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "加载失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/document/{id}/save
     * Body: { "html": "<p>编辑后的内容</p>", "fileType": "docx" }
     * 将 HTML 转换回原格式覆盖保存
     */
    @PostMapping("/{id}/save")
    public ResponseEntity<Map<String, Object>> saveContent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Map<String, Object> result = new HashMap<>();
        try {
            String html = body.get("html");
            String fileType = body.get("fileType");
            if (html == null || fileType == null) {
                result.put("code", 400);
                result.put("msg", "缺少参数");
                return ResponseEntity.badRequest().body(result);
            }

            DocFile docFile = fileRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("文档不存在"));

            String storageType = docFile.getStorageType() != null ? docFile.getStorageType() : "local";

            // MinIO 存储：先下载到本地临时文件
            java.nio.file.Path minioTemp = null;
            String filePath;
            if ("minio".equalsIgnoreCase(storageType)) {
                String ext = docFile.getFileType() != null ? docFile.getFileType() : "tmp";
                minioTemp = Files.createTempFile("doc_save_", "." + ext);
                try (InputStream is = storageRouter.download(storageType, docFile.getFilePath())) {
                    Files.copy(is, minioTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                filePath = minioTemp.toString();
            } else {
                filePath = getActualFilePath(docFile);
                if (filePath == null || !Files.exists(Paths.get(filePath))) {
                    result.put("code", 404);
                    result.put("msg", "文件不存在于存储路径");
                    return ResponseEntity.status(404).body(result);
                }
            }

            // 原文件的扩展名
            String origExt = "." + docFile.getFileType().toLowerCase();
            String baseDir = System.getProperty("java.io.tmpdir") + File.separator + "doc_edit" + File.separator;
            new File(baseDir).mkdirs();

            // 把 HTML 写入临时文件
            String htmlPath = baseDir + id + "_edit.html";
            String styledHtml = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
                    "<style>body{font-family:'Microsoft YaHei',sans-serif;line-height:1.8;padding:20px}</style>" +
                    "</head><body>" + html + "</body></html>";
            Files.writeString(Paths.get(htmlPath), styledHtml);

            // 用 LibreOffice 转回原格式
            String outPath = baseDir + id + "_output" + origExt;
            String soffice = findSofficeCmd();
            ProcessBuilder pb = new ProcessBuilder(
                    soffice, "--headless", "--convert-to", origExt.replace(".", ""),
                    "--outdir", baseDir, htmlPath
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

            // 清理临时 HTML
            new File(htmlPath).delete();

            if (finished && p.exitValue() == 0 && new File(outPath).exists()) {
                // 覆盖原文件
                Files.copy(Paths.get(outPath), Paths.get(filePath),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                new File(outPath).delete();

                // MinIO 存储：上传回 MinIO
                if ("minio".equalsIgnoreCase(storageType)) {
                    try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
                        storageRouter.getStorage(storageType).upload(
                                is, docFile.getFilePath(),
                                Files.size(Paths.get(filePath)),
                                "application/octet-stream");
                    } catch (Exception e) {
                        result.put("code", 500);
                        result.put("msg", "上传 MinIO 失败: " + e.getMessage());
                        return ResponseEntity.ok(result);
                    }
                }

                result.put("code", 200);
                result.put("msg", "保存成功");
            } else {
                result.put("code", 200);
                result.put("msg", "已保存为HTML（格式转换失败）");
            }

            // 清理 MinIO 临时文件
            if (minioTemp != null) {
                try { Files.deleteIfExists(minioTemp); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "保存失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 从数据库获取文件的真实存储路径
     */
    private String getActualFilePath(DocFile docFile) {
        String dbPath = docFile.getFilePath();
        if (dbPath == null || dbPath.isEmpty()) return null;

        // 如果数据库里的路径已经是绝对路径，直接使用
        if (new File(dbPath).isAbsolute()) return dbPath;

        // 否则拼接项目基路径（去掉开头的 ./ 或 .\）
        String relativePath = dbPath.replaceFirst("^\\.+[/\\\\]", "");
        return "D:\\java-xiangmu\\private_cloud_back\\" + relativePath;
    }

    private String findSofficeCmd() {
        String[] candidates = {
                "soffice",
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"
        };
        for (String cmd : candidates) {
            if (new File(cmd).exists() || cmd.equals("soffice")) {
                if (cmd.equals("soffice")) return cmd;
                return cmd;
            }
        }
        throw new RuntimeException("未找到 soffice.exe，请手动添加 PATH 或检查安装路径");
    }
}
