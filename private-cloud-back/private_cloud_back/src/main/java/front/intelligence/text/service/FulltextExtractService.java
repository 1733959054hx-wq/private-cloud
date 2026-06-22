package front.intelligence.text.service;

import front.search.engine.es.DocFileIndexService;
import front.system.service.SensitiveWordService;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hslf.extractor.QuickButCruddyTextExtractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 全类型文件文本提取服务
 * 支持格式：txt/md/csv/xml/json/yaml/doc/docx/xls/xlsx/ppt/pptx/pdf/html/images
 */
@Service
public class FulltextExtractService {

    @Autowired
    private DocFileRepository docFileRepository;

    @Autowired(required = false)
    private DocFileIndexService docFileIndexService;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    @Lazy
    private FulltextExtractService selfProxy;  // 自注入，避免 self-invocation 绕过事务

    @Autowired
    private front.storage.service.StorageRouter storageRouter;

    @Autowired
    private front.storage.service.StorageHelper storageHelper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public String getUploadDir() { return uploadDir; }

    /**
     * 提取单个文件的全文内容并保存到数据库
     */
    @Transactional
    public ExtractResult extractAndSave(Long fileId) {
        DocFile docFile = docFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在, id=" + fileId));

        String text = extractText(docFile);

        ExtractResult result = new ExtractResult();
        result.setFileId(fileId);
        result.setFileName(docFile.getFileName());

        if (text == null) {
            result.setSuccess(false);
            result.setErrorMsg("文本提取失败（文件路径: " + docFile.getFilePath() + "）");
            return result;
        }

        // 签章文件检测：文件名含 _已签章 则标记跳过 AI 处理
        String fileName = docFile.getFileName();
        if (fileName != null && fileName.contains("_已签章")) {
            text = "[签章PDF：内容不可读]";
            result.setSkippedForAi(true);
        }

        List<String> sensitiveWords = sensitiveWordService.findSensitiveWords(text);
        if (!sensitiveWords.isEmpty()) {
            text = sensitiveWordService.filterSensitiveWord(text);
            result.setSensitiveWords(sensitiveWords);
            System.out.println("[文本提取] 检测到敏感词, fileId=" + fileId + ", words=" + sensitiveWords);
        }

        docFile.setFulltextContent(text);
        docFileRepository.save(docFile);

        // 同步到 Elasticsearch 索引
        try {
            if (docFileIndexService != null) {
                docFileIndexService.syncDocument(docFile);
            }
        } catch (Exception e) {
            System.err.println("[文本提取] ES 索引同步失败, fileId=" + fileId + ", error=" + e.getMessage());
        }

        result.setSuccess(true);
        result.setTextLength(text.length());
        return result;
    }

    /**
     * 提取所有未提取全文内容的文件
     * 注：不在此方法上加 @Transactional，每个文件在 extractAndSave 里独立提交
     */
    public List<ExtractResult> extractAllPending() {
        List<DocFile> pendingFiles = docFileRepository.findByDeletedAndStatus(0, 1);
        List<ExtractResult> results = new ArrayList<>();

        int total = pendingFiles.size();
        int done = 0;
        for (DocFile file : pendingFiles) {
            if (file.getFulltextContent() != null && !file.getFulltextContent().isEmpty()) {
                done++;
                continue;
            }
            System.out.println("[批量提取] " + (++done) + "/" + total + " " + file.getFileName());
            try {
                    results.add(selfProxy.extractAndSave(file.getId()));
                } catch (Exception e) {
                ExtractResult err = new ExtractResult();
                err.setFileId(file.getId());
                err.setFileName(file.getFileName());
                err.setSuccess(false);
                err.setErrorMsg(e.getMessage());
                results.add(err);
            }
        }
        return results;
    }

    /**
     * 异步提取单个文件的全文内容并保存（上传完成后自动调用）
     */
    @Async("ocrExecutor")
    @Transactional
    public void extractAsync(Long fileId) {
        try {
            extractAndSave(fileId);
            System.out.println("[文本提取] 自动提取完成, fileId=" + fileId);
        } catch (Exception e) {
            System.err.println("[文本提取] 自动提取失败, fileId=" + fileId + ", error=" + e.getMessage());
        }
    }

    /**
     * 提取文件文本内容（不保存到数据库，仅返回文本）
     */
    public String extractText(DocFile docFile) {
        if (docFile == null) return null;

        String filePath = docFile.getFilePath();
        if (filePath == null || filePath.isEmpty()) return null;

        String ext = docFile.getFileType();
        if (ext == null) {
            // 从文件名推断扩展名
            String name = docFile.getFileName();
            int idx = name.lastIndexOf('.');
            if (idx > 0) ext = name.substring(idx + 1).toLowerCase();
        }
        if (ext == null) return null;
        ext = ext.toLowerCase();

        String storageType = docFile.getStorageType() != null ? docFile.getStorageType() : "local";

        // MinIO 存储：下载到本地临时文件后提取
        if ("minio".equalsIgnoreCase(storageType)) {
            String tempSuffix = "." + (ext.isEmpty() ? "tmp" : ext);
            java.nio.file.Path tempFile = null;
            try {
                tempFile = storageHelper.ensureLocalAccessible(storageType, filePath, "extract_", tempSuffix);
                File localFile = tempFile.toFile();
                if (!localFile.exists()) return null;
                return extractTextFromFile(localFile, ext);
            } catch (Exception e) {
                System.err.println("[文本提取] 从 MinIO 下载文件失败: " + filePath + ", error=" + e.getMessage());
                return null;
            } finally {
                if (tempFile != null) {
                    storageHelper.cleanupTempFile(tempFile);
                }
            }
        }

        // 本地存储：保持原有逻辑
        File file = resolveFile(filePath);
        if (file == null || !file.exists()) return null;

        return extractTextFromFile(file, ext);
    }

    /**
     * 实际执行文本提取（基于本地文件）
     */
    private String extractTextFromFile(File file, String ext) {
        try {
            switch (ext) {
                case "txt":
                case "csv":
                case "xml":
                case "json":
                case "yaml":
                case "yml":
                case "log":
                case "properties":
                case "ini":
                case "cfg":
                    return new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);

                case "md":
                    return cleanMarkdown(new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));

                case "html":
                case "htm":
                    return extractHtmlText(file);

                case "pdf":
                    String pdfText = extractPdfText(file);
                    // 如果提取文字很少，可能是扫描件PDF，提示走OCR
                    if (pdfText != null && pdfText.length() < 100 && pdfText.contains("扫描")) {
                        return pdfText + "\n\n[提示：此PDF可能是扫描件，文字内容需通过OCR识别]";
                    }
                    return pdfText;

                case "docx":
                    return extractDocxText(file);

                case "doc":
                    return extractDocText(file);

                case "xlsx":
                    return extractXlsxText(file);

                case "xls":
                    return extractXlsText(file);

                case "pptx":
                    return extractPptxText(file);

                case "ppt":
                    return extractPptText(file);

                case "jpg":
                case "jpeg":
                case "png":
                case "bmp":
                case "tiff":
                case "tif":
                case "gif":
                case "webp":
                    return "[图片文件：仅支持标题搜索]";

                case "mp4":
                case "avi":
                case "mov":
                case "wmv":
                case "flv":
                case "mkv":
                case "mp3":
                case "wav":
                case "wma":
                case "aac":
                case "flac":
                    return "[多媒体文件：仅支持标题搜索]";

                case "zip":
                case "rar":
                case "7z":
                case "tar":
                case "gz":
                case "bz2":
                    return "[压缩文件：仅支持标题搜索]";

                case "exe":
                case "dll":
                case "so":
                case "dmg":
                case "iso":
                case "bin":
                    return "[二进制文件：仅支持标题搜索]";

                default:
                    // 对于未知扩展名，尝试检测是否为文本文件
                    try {
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        // 检查是否包含大量空字节（二进制文件特征）
                        int nullCount = 0;
                        int checkLen = Math.min(bytes.length, 4096);
                        for (int i = 0; i < checkLen; i++) {
                            if (bytes[i] == 0) nullCount++;
                        }
                        if (nullCount > checkLen / 10) {
                            return "[二进制文件：仅支持标题搜索]";
                        }
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                        return null;
                    }
            }
        } catch (Exception e) {
            System.err.println("[文本提取] 提取失败: " + file.getAbsolutePath() + ", error=" + e.getMessage());
            return null;
        }
    }

    // ========== 文件解析 ==========

    private File resolveFile(String filePath) {
        if (filePath == null) return null;
        // filePath 存储为相对路径
        File file = new File(filePath);
        if (file.exists()) {
            System.out.println("[文本提取] 路径1(直接): " + file.getAbsolutePath());
            return file;
        }
        // 以项目工作目录为基准
        file = new File(System.getProperty("user.dir"), filePath);
        if (file.exists()) {
            System.out.println("[文本提取] 路径2(user.dir): " + file.getAbsolutePath());
            return file;
        }
        // 以 uploadDir 为基准（兼容uploadDir与filePath不同的情况）
        String cleanPath = filePath.replaceAll("^[\\\\/.]*uploads?[\\\\/]", "");
        file = new File(uploadDir, cleanPath);
        if (file.exists()) {
            System.out.println("[文本提取] 路径3(uploadDir): " + file.getAbsolutePath());
            return file;
        }
        System.err.println("[文本提取] 文件未找到: filePath=" + filePath + ", user.dir=" + System.getProperty("user.dir") + ", uploadDir=" + uploadDir);
        return null;
    }

    // ========== PDF ==========

    /**
     * 提取 PDF 全文（向后兼容：拼接所有页文本，每页前注入【第N页】锚点标记）
     * 该锚点会被 AiQaServiceImpl 的 Prompt 识别，用于生成带页码的引用，
     * 前端可据此实现"点击引用跳转到对应页"的联动效果
     */
    private String extractPdfText(File file) throws Exception {
        java.util.List<PageText> pages = extractPdfTextByPage(file);
        if (pages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PageText p : pages) {
            if (sb.length() > 0) sb.append("\n\n");
            // 注入页码锚点：大模型可识别并保留到回答中
            sb.append("【第").append(p.getPageNumber()).append("页】\n");
            sb.append(p.getText());
        }
        return sb.toString().trim();
    }

    /**
     * 按页提取 PDF 文本，返回带页码的结构化数据
     * 用于"一键脑图生成"功能，前端可基于页码实现动态跳转
     */
    public java.util.List<PageText> extractPdfTextByPage(File file) throws Exception {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<java.util.List<PageText>> future = executor.submit(() -> {
            try (org.apache.pdfbox.pdmodel.PDDocument doc = Loader.loadPDF(file)) {
                int pageCount = doc.getNumberOfPages();
                java.util.List<PageText> pages = new java.util.ArrayList<>(pageCount);
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                for (int i = 1; i <= pageCount; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(doc).trim();
                    pages.add(new PageText(i, pageText));
                }
                return pages;
            }
        });
        try {
            return future.get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            System.out.println("  → PDF按页提取超时(60s)，跳过: " + file.getName());
            future.cancel(true);
            return java.util.Collections.emptyList();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 页面文本结构（带页码），用于脑图生成与动态跳转
     */
    public static class PageText {
        private final int pageNumber;
        private final String text;

        public PageText(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text;
        }

        public int getPageNumber() { return pageNumber; }
        public String getText() { return text; }
    }

    // ========== DOCX ==========

    private String extractDocxText(File file) throws Exception {
        // 优先使用 LibreOffice（保留格式）
        String libreText = tryLibreOfficeText(file);
        if (libreText != null) return libreText;

        // 方式一：使用 POI XWPF
        try {
            OPCPackage opc = OPCPackage.open(file);
            XWPFWordExtractor extractor = new XWPFWordExtractor(opc);
            String text = extractor.getText();
            extractor.close();
            opc.close();
            if (text != null && !text.trim().isEmpty()) return text.trim();
        } catch (Exception ignored) {}

        // 方式二：ZIP 解析降级（直接正则匹配 XML 中的段落文本）
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] entryBytes = readZipEntryBytes(zis);
                    String xml = new String(entryBytes, java.nio.charset.StandardCharsets.UTF_8);
                    // DOCX 中段落文本在 <w:p> 或 <p> 中
                    var p = java.util.regex.Pattern.compile("<(?:w:)?p[^>]*>(.*?)</(?:w:)?p[^>]*>", java.util.regex.Pattern.DOTALL);
                    var m = p.matcher(xml);
                    while (m.find()) {
                        // 提取段落内的纯文本（去掉所有 XML 标签）
                        String paraText = m.group(1).replaceAll("<[^>]+>", "").trim();
                        if (!paraText.isEmpty()) sb.append(paraText).append("\n");
                    }
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    // ========== DOC（老版 Word） ==========

    private String extractDocText(File file) throws Exception {
        try (WordExtractor extractor = new WordExtractor(new FileInputStream(file))) {
            return extractor.getText().trim();
        } catch (Exception e) {
            System.out.println("  → .doc POI提取失败(" + e.getClass().getSimpleName() + ")，尝试 docx 降级: " + file.getName());
            try {
                return extractDocxText(file);
            } catch (Exception ignored) {}
            return "[Word文档：提取失败]";
        }
    }

    // ========== XLSX ==========

    private String extractXlsxText(File file) throws Exception {
        // 优先使用 LibreOffice
        String libreText = tryLibreOfficeText(file);
        if (libreText != null) return libreText;

        try {
            OPCPackage opc = OPCPackage.open(file);
            XSSFExcelExtractor extractor = new XSSFExcelExtractor(opc);
            extractor.setFormulasNotResults(false);
            extractor.setIncludeSheetNames(true);
            String text = extractor.getText();
            extractor.close();
            opc.close();
            if (text != null && !text.trim().isEmpty()) return text.trim();
        } catch (Exception ignored) {}

        // 降级：ZIP 解析 sharedStrings + sheet
        return extractXlsxByZip(file);
    }

    private String extractXlsxByZip(File file) throws Exception {
        java.util.Map<String, String> sharedStrings = new java.util.HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    String xml = readStream(zis);
                    var p = java.util.regex.Pattern.compile("<t[^>]*>(.*?)</t>");
                    var m = p.matcher(xml);
                    int idx = 0;
                    while (m.find()) sharedStrings.put(String.valueOf(idx++), m.group(1));
                    break;
                }
            }
        }

        StringBuilder result = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("worksheets/sheet") && name.endsWith(".xml")) {
                    String xml = readStream(zis);
                    var cellP = java.util.regex.Pattern.compile("<c[^>]*t=\"s\"[^>]*>.*?<v>(\\d+)</v>.*?</c>|<c[^>]*>.*?<v>([^<]+)</v>.*?</c>");
                    var m = cellP.matcher(xml);
                    while (m.find()) {
                        String val = m.group(1) != null ? sharedStrings.get(m.group(1)) : m.group(2);
                        if (val != null && !val.trim().isEmpty()) result.append(val).append(" ");
                    }
                }
            }
        }
        String text = result.toString().trim();
        if (text.isEmpty()) text = String.join(" ", sharedStrings.values());
        return text;
    }

    // ========== XLS（老版 Excel） ==========

    private String extractXlsText(File file) throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook(new FileInputStream(file));
             ExcelExtractor extractor = new ExcelExtractor(wb)) {
            extractor.setFormulasNotResults(false);
            extractor.setIncludeSheetNames(true);
            return extractor.getText().trim();
        }
    }

    // ========== PPTX ==========

    private String extractPptxText(File file) throws Exception {
        // 尝试 LibreOffice（更准确的格式化提取）
        String libreText = tryLibreOfficeText(file);
        if (libreText != null) return libreText;

        // 降级：ZIP 解析（去掉所有 XML 标签提取纯文本）
        StringBuilder text = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("ppt/slides/slide") && entry.getName().endsWith(".xml")) {
                    byte[] entryBytes = readZipEntryBytes(zis);
                    String xml = new String(entryBytes, java.nio.charset.StandardCharsets.UTF_8);
                    // 去掉所有 XML 标签，只保留标签之间的纯文本
                    String plain = xml.replaceAll("<[^>]+>", " ").trim();
                    // 合并多个空格和换行为单个换行
                    plain = plain.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
                    if (!plain.isEmpty()) text.append(plain).append("\n\n");
                }
            }
        }
        return text.toString().trim();
    }

    // ========== PPT（老版 PowerPoint） ==========

    private String extractPptText(File file) throws Exception {
        // 优先使用 LibreOffice
        String libreText = tryLibreOfficeText(file);
        if (libreText != null) return libreText;

        QuickButCruddyTextExtractor extractor = null;
        try {
            extractor = new QuickButCruddyTextExtractor(new java.io.FileInputStream(file));
            String text = extractor.getTextAsString();
            if (text != null && !text.trim().isEmpty()) return text.trim();
        } catch (Exception e) {
            // 部分 .ppt 文件实际是 OOXML（.pptx）格式
            if (e.getMessage() != null && e.getMessage().contains("OOXML")) {
                System.out.println("  → .ppt 文件实为 OOXML 格式，尝试 pptx 解析");
                return extractPptxText(file);
            }
        } finally {
            if (extractor != null) extractor.close();
        }
        // 提取结果为空或失败，尝试 ppxt 降级
        return extractPptxText(file);
    }

    // ========== HTML ==========

    private String extractHtmlText(File file) throws Exception {
        String html = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        return extractHtmlText(html);
    }

    // ========== LibreOffice 辅助 ==========

    /**
     * 使用 LibreOffice 将文件转为 HTML 后提取文本
     * @return 提取的纯文本，如果 LibreOffice 不可用返回 null
     */
    private String tryLibreOfficeText(File file) {
        String[] cmdCandidates = {
                "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
                "soffice"
        };

        String tmpDir = uploadDir + "/tmp/";
        new java.io.File(tmpDir).mkdirs();
        String baseName = file.getName().replaceAll("\\.[^.]+$", "");

        for (String cmd : cmdCandidates) {
            java.io.File cmdFile = new java.io.File(cmd);
            if (!cmdFile.exists()) continue;

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        cmd, "--headless", "--convert-to", "html:HTML",
                        "--outdir", tmpDir, file.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        while (br.readLine() != null) {}
                    } catch (Exception ignored) {}
                }).start();
                boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) { p.destroyForcibly(); continue; }

                String htmlPath = tmpDir + baseName + ".html";
                java.io.File htmlFile = new java.io.File(htmlPath);
                if (htmlFile.exists()) {
                    if (htmlFile.length() < 1024) {
                        java.io.File[] matches = new java.io.File(tmpDir).listFiles((dir, name) ->
                                name.startsWith(baseName) && name.endsWith(".html") && !name.equals(baseName + ".html"));
                        if (matches != null && matches.length > 0) htmlFile = matches[0];
                    }
                    String html = new String(Files.readAllBytes(htmlFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    htmlFile.delete();
                    System.out.println("  → LibreOffice 提取成功: " + file.getName() + " (" + html.length() + " chars)");
                    return extractHtmlText(new java.io.ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
            } catch (Exception ignored) {}
        }
        System.out.println("  → LibreOffice 不可用，使用降级方案: " + file.getName());
        return null;
    }

    /**
     * 从 HTML 字符串中提取纯文本
     */
    private String extractHtmlText(InputStream htmlStream) throws Exception {
        String html = new String(readStream(htmlStream).getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return extractHtmlText(html);
    }

    private String extractHtmlText(String html) {
        // 去除 script 和 style 标签
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        // 块级标签转换行
        html = html.replaceAll("(?is)<br\\s*/?>", "\n");
        html = html.replaceAll("(?is)</p>", "\n");
        html = html.replaceAll("(?is)</tr>", "\n");
        html = html.replaceAll("(?is)</div>", "\n");
        html = html.replaceAll("(?is)</h[1-6]>", "\n");
        html = html.replaceAll("(?is)</li>", "\n");
        // 去除所有标签
        html = html.replaceAll("<[^>]+>", "");
        // 解码 HTML 实体
        html = html.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        // 合并多余空行
        html = html.replaceAll("\\n{3,}", "\n\n");
        return html.trim();
    }

    // ========== 工具 ==========

    /**
     * 清理 Markdown 格式符号，只保留纯文本内容
     */
    private String cleanMarkdown(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        // 移除标题标记：### 标题 → 标题
        raw = raw.replaceAll("(?m)^#{1,6}\\s+", "");
        // 移除分隔线：---、***、___
        raw = raw.replaceAll("(?m)^[-*_]{3,}\\s*$", "");
        // 移除表格分隔线行：| --- | --- |
        raw = raw.replaceAll("(?m)^\\|[\\s-:|]+\\|\\s*$", "");
        // 移除列表标记：- item、* item、+ item、1. item
        raw = raw.replaceAll("(?m)^[\\s]*(?:[-*+]|\\d+\\.)\\s+", "");
        // 移除代码块标记：``` 或 ~~~
        raw = raw.replaceAll("(?m)^[`~]{3,}.*$", "");
        // 移除行内代码标记：`code`
        raw = raw.replaceAll("`([^`]+)`", "$1");
        // 移除加粗/斜体：**text**、*text*、__text__、_text_
        raw = raw.replaceAll("\\*{1,2}([^*]+)\\*{1,2}", "$1");
        raw = raw.replaceAll("_{1,2}([^_]+)_{1,2}", "$1");
        // 移除链接： [text](url) → text
        raw = raw.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        // 移除图片： ![alt](url)
        raw = raw.replaceAll("!\\[[^\\]]*\\]\\([^)]+\\)", "");
        // 移除引用标记：> text → text
        raw = raw.replaceAll("(?m)^>\\s*", "");
        // 移除HTML标签
        raw = raw.replaceAll("<[^>]+>", "");
        // 合并多余空行
        raw = raw.replaceAll("\\n{3,}", "\n\n");
        raw = raw.replaceAll("(?m)^[ \t]+", "");
        return raw.trim();
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    /**
     * 读取 ZipInputStream 当前 entry 的全部字节
     * 避免 DocumentBuilder.parse() 直接解析流时导致 Stream closed 错误
     */
    private byte[] readZipEntryBytes(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ========== 结果 DTO ==========

    public static class ExtractResult {
        private Long fileId;
        private String fileName;
        private boolean success;
        private int textLength;
        private String errorMsg;
        private List<String> sensitiveWords;
        private boolean skippedForAi;

        public boolean isSkippedForAi() { return skippedForAi; }
        public void setSkippedForAi(boolean skippedForAi) { this.skippedForAi = skippedForAi; }
        public List<String> getSensitiveWords() { return sensitiveWords; }
        public void setSensitiveWords(List<String> sensitiveWords) { this.sensitiveWords = sensitiveWords; }

        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getTextLength() { return textLength; }
        public void setTextLength(int textLength) { this.textLength = textLength; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    }
}
