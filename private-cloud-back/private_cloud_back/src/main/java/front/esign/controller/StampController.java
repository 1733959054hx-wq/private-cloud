package front.esign.controller;

import front.esign.service.OfficeConverter;
import front.esign.service.StampService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 模拟电子签章控制器
 * 支持 PDF 直接签章，Office 文档自动转 PDF 后签章
 */
@RestController
@RequestMapping("/api/esign")
public class StampController {

    @Resource
    private StampService stampService;

    @Resource
    private OfficeConverter officeConverter;

    /**
     * 对指定文档加盖电子签章
     * POST /api/esign/stamp/{documentId}?signer=张三
     */
    @PostMapping("/stamp/{documentId}")
    public ResponseEntity<?> stampDocument(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "系统管理员") String signer) {

        try {
            // 查找文件
            String filePath = findFilePath(documentId);
            if (filePath == null) {
                return err(404, "未找到文档");
            }

            // 如果不是 PDF，先转成 PDF
            String pdfPath;
            if (filePath.toLowerCase().endsWith(".pdf")) {
                pdfPath = filePath;
            } else {
                pdfPath = filePath.substring(0, filePath.lastIndexOf('.')) + ".pdf";
                officeConverter.convertToPdf(filePath, pdfPath);
            }

            // 盖章
            byte[] pdfBytes = Files.readAllBytes(Paths.get(pdfPath));
            byte[] stampedPdf;
            try (InputStream is = new ByteArrayInputStream(pdfBytes)) {
                stampedPdf = stampService.stampPdf(is, signer);
            }

            // 保存带章的 PDF
            Files.write(Paths.get(pdfPath), stampedPdf);

            Map<String, Object> ok = new HashMap<>();
            ok.put("code", 200);
            ok.put("msg", "签章完成");
            return ResponseEntity.ok(ok);

        } catch (Exception e) {
            e.printStackTrace();
            String filePath = findFilePath(documentId);
            long size = -1;
            try { size = Files.size(Paths.get(filePath)); } catch (Exception ignored) {}; 
            String detail = "盖章失败：" + e.getMessage()
                    + " | 查找路径: " + (filePath != null ? filePath : "未找到")
                    + " | 文件大小: " + size + " 字节"
                    + " | upload目录: D:\\java-xiangmu\\private_cloud_back\\upload\\";
            return err(500, detail);
        }
    }

    private String findFilePath(Long documentId) {
        String base = "D:\\java-xiangmu\\private_cloud_back\\upload\\";
        // 打印目录内容方便调试
        File dir = new File(base);
        String[] files = dir.list();
        System.out.println("upload目录文件列表: " + java.util.Arrays.toString(files));

        // 优先找 Office 文件（有转换逻辑），最后才找 PDF
        String[] exts = {
                ".doc", ".DOC", ".docx", ".DOCX", ".docm", ".DOCM",
                ".xls", ".XLS", ".xlsx", ".XLSX", ".xlsm", ".XLSM", ".xlsb", ".XLSB", ".csv", ".CSV",
                ".ppt", ".PPT", ".pptx", ".PPTX", ".pptm", ".PPTM",
                ".odt", ".ODT", ".ods", ".ODS", ".odp", ".ODP",
                ".rtf", ".RTF", ".txt", ".TXT",
                ".pdf", ".PDF"
        };
        for (String ext : exts) {
            String path = base + documentId + ext;
            if (Files.exists(Paths.get(path))) return path;
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> err(int code, String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("msg", msg);
        return ResponseEntity.status(code).body(err);
    }
}
