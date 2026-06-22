package front.esign.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Office 文档转 PDF 服务
 * 先尝试 LibreOffice（保留原格式），不可用时降级为纯文本提取
 */
@Service
public class OfficeConverter {

    public void convertToPdf(String inputPath, String outputPath) throws Exception {
        long fileSize = Files.size(Paths.get(inputPath));
        if (fileSize == 0) { createBlankPdf(outputPath, "（空文档）"); return; }

        String lower = inputPath.toLowerCase();

        // PDF 直接复制
        if (lower.endsWith(".pdf")) {
            Files.copy(Paths.get(inputPath), Paths.get(outputPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        // LibreOffice 优先（保留格式）
        if (tryLibreOffice(inputPath, outputPath)) return;

        // LibreOffice 不可用 → 降级为纯文本提取（丢失格式）
        System.out.println("  → LibreOffice 未安装或转换失败，降级为纯文本模式");
        try {
            if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
                convertDocxToPdf(inputPath, outputPath);
            } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                convertXlsxToPdf(inputPath, outputPath);
            } else if (lower.endsWith(".pptx") || lower.endsWith(".ppt")) {
                convertPptxToPdf(inputPath, outputPath);
            } else if (lower.endsWith(".pdf")) {
                Files.copy(Paths.get(inputPath), Paths.get(outputPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                // 其他格式尝试当作文本读取生成PDF
                try {
                    String text = Files.readString(Paths.get(inputPath));
                    writeTextToPdf(text.isEmpty() ? "（内容为空）" : text, outputPath);
                } catch (Exception e2) {
                    createBlankPdf(outputPath, "（无法解析文档）");
                }
            }
        } catch (Exception e2) {
            createBlankPdf(outputPath, "（转换失败）");
        }
    }

    /** 提取文档纯文本内容（用于协同编辑器加载） */
    public String extractText(String inputPath) throws Exception {
        long fileSize = Files.size(Paths.get(inputPath));
        if (fileSize == 0) return "";
        String lower = inputPath.toLowerCase();

        // 纯文本格式直接读
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")
                || lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return Files.readString(Paths.get(inputPath));
        }

        // 尝试 LibreOffice 转 HTML（保留格式）
        String outDir = "D:\\java-xiangmu\\private_cloud_back\\upload\\tmp\\";
        new File(outDir).mkdirs();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "soffice", "--headless", "--convert-to", "html:HTML",
                    "--outdir", outDir, inputPath
            );
            pb.redirectErrorStream(true);
            Process p = pb.start(); p.waitFor();
            String htmlPath = outDir + new File(inputPath).getName().replaceAll("\\.[^.]+$", "") + ".html";
            File htmlFile = new File(htmlPath);
            if (htmlFile.exists()) {
                String html = Files.readString(htmlFile.toPath());
                htmlFile.delete();
                return html;
            }
        } catch (Exception ignored) {}

        // 降级：docx/xlsx/pptx ZIP 解析
        if (lower.endsWith(".docx")) {
            StringBuilder sb = new StringBuilder();
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(inputPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if ("word/document.xml".equals(entry.getName())) {
                        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                        f.setNamespaceAware(false);
                        org.w3c.dom.Document xml = f.newDocumentBuilder().parse(zis);
                        org.w3c.dom.NodeList ps = xml.getElementsByTagName("p");
                        for (int i = 0; i < ps.getLength(); i++)
                            sb.append(ps.item(i).getTextContent()).append("\n");
                        break;
                    }
                }
            }
            return sb.toString().trim();
        }
        if (lower.endsWith(".pdf")) {
            // PDF 用 PDFBox 提取纯文本
            try (PDDocument doc = Loader.loadPDF(new File(inputPath))) {
                return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            }
        }
        throw new RuntimeException("不支持的文件格式: " + inputPath);
    }

    /** LibreOffice 转换（保留原格式） */
    private boolean tryLibreOffice(String inputPath, String outputPath) {
        String pf = System.getenv("PROGRAMFILES");
        String pf86 = System.getenv("PROGRAMFILES(X86)");
        System.out.println("  [LO调试] PROGRAMFILES='" + pf + "', PROGRAMFILES(X86)='" + pf86 + "'");
        System.out.println("  [LO调试] inputPath=" + inputPath + ", outputPath=" + outputPath);

        // 收集候选路径，用 LinkedHashSet 去重
        java.util.LinkedHashSet<String> candidateSet = new java.util.LinkedHashSet<>();
        candidateSet.add("C:\\Program Files\\LibreOffice\\program\\soffice.exe");
        candidateSet.add("soffice");
        if (pf != null) {
            candidateSet.add(pf + "\\LibreOffice\\program\\soffice.exe");
            candidateSet.add(pf + "\\LibreOffice 26\\program\\soffice.exe");
            candidateSet.add(pf + "\\LibreOffice 25\\program\\soffice.exe");
            candidateSet.add(pf + "\\LibreOffice 24\\program\\soffice.exe");
        }
        if (pf86 != null) {
            candidateSet.add(pf86 + "\\LibreOffice\\program\\soffice.exe");
            candidateSet.add(pf86 + "\\LibreOffice 26\\program\\soffice.exe");
            candidateSet.add(pf86 + "\\LibreOffice 25\\program\\soffice.exe");
            candidateSet.add(pf86 + "\\LibreOffice 24\\program\\soffice.exe");
        }

        int libOTimeout = 180; // 秒
        for (String cmd : candidateSet) {
            if (cmd == null || cmd.isBlank()) continue;
            System.out.println("  [LO调试] 尝试路径: " + cmd);
            // 先检查可执行文件是否存在
            if (!cmd.equals("soffice") && !new File(cmd).exists()) {
                System.out.println("  [LO调试]   → 文件不存在，跳过");
                continue;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        cmd, "--headless", "--norestore", "--convert-to", "pdf",
                        "--outdir", new File(outputPath).getParent(),
                        inputPath
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                // 收集输出用于调试
                StringBuilder output = new StringBuilder();
                Thread reader = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    } catch (Exception ignored) {}
                });
                reader.start();
                boolean finished = p.waitFor(libOTimeout, TimeUnit.SECONDS);
                reader.join(2000);
                if (finished) {
                    System.out.println("  [LO调试]   → 退出码: " + p.exitValue());
                    if (output.length() > 0) {
                        System.out.println("  [LO调试]   → 输出: " + output.toString().trim());
                    }
                } else {
                    System.out.println("  [LO调试]   → 超时(" + libOTimeout + "s)，强制终止");
                    p.destroyForcibly();
                    continue;
                }
                if (new File(outputPath).exists() && p.exitValue() <= 1) {
                    System.out.println("  → LibreOffice 转换成功（保留原格式，退出码=" + p.exitValue() + "）");
                    return true;
                } else {
                    System.out.println("  [LO调试]   → 输出文件是否存在: " + new File(outputPath).exists());
                }
            } catch (Exception e) {
                System.out.println("  [LO调试]   → 异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return false;
    }

    // ========== XLSX 转 PDF ==========
    private void convertXlsxToPdf(String xlsxPath, String pdfPath) throws Exception {
        java.util.Map<String, String> sharedStrings = new java.util.HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(xlsxPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/sharedStrings.xml".equals(entry.getName())) {
                    String xml = readStream(zis);
                    Pattern p = Pattern.compile("<t[^>]*>(.*?)</t>");
                    java.util.regex.Matcher m = p.matcher(xml);
                    int idx = 0;
                    while (m.find()) sharedStrings.put(String.valueOf(idx++), m.group(1));
                    break;
                }
            }
        }

        StringBuilder result = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(xlsxPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("worksheets/sheet") && name.endsWith(".xml")) {
                    String xml = readStream(zis);
                    Pattern cellP = Pattern.compile("<c[^>]*t=\"s\"[^>]*>.*?<v>(\\d+)</v>.*?</c>|<c[^>]*>.*?<v>([^<]+)</v>.*?</c>");
                    java.util.regex.Matcher m = cellP.matcher(xml);
                    while (m.find()) {
                        String val = m.group(1) != null ? sharedStrings.get(m.group(1)) : m.group(2);
                        if (val != null && !val.trim().isEmpty()) result.append(val).append(" ");
                    }
                    break;
                }
            }
        }

        String text = result.toString().trim();
        if (text.isEmpty()) text = String.join(" ", sharedStrings.values());
        writeTextToPdf(text.isEmpty() ? "（表格内容为空）" : text, pdfPath);
    }

    // ========== DOCX 转 PDF ==========
    private void convertDocxToPdf(String docxPath, String pdfPath) throws Exception {
        StringBuilder textBuilder = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(docxPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                    f.setNamespaceAware(false);
                    org.w3c.dom.Document xml = f.newDocumentBuilder().parse(zis);
                    org.w3c.dom.NodeList paragraphs = xml.getElementsByTagName("p");
                    if (paragraphs.getLength() > 0) {
                        for (int i = 0; i < paragraphs.getLength(); i++)
                            textBuilder.append(paragraphs.item(i).getTextContent()).append("\n");
                    } else {
                        textBuilder.append(xml.getDocumentElement().getTextContent());
                    }
                    break;
                }
            }
        }
        writeTextToPdf(textBuilder.toString().trim(), pdfPath);
    }

    // ========== PPTX 转 PDF ==========
    private void convertPptxToPdf(String pptxPath, String pdfPath) throws Exception {
        StringBuilder text = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(pptxPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("ppt/slides/slide") && entry.getName().endsWith(".xml")) {
                    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                    f.setNamespaceAware(false);
                    org.w3c.dom.Document xml = f.newDocumentBuilder().parse(zis);
                    org.w3c.dom.NodeList ts = xml.getElementsByTagName("t");
                    for (int i = 0; i < ts.getLength(); i++) {
                        String t = ts.item(i).getTextContent();
                        if (t.trim().length() > 0) text.append(t).append("\n");
                    }
                    text.append("---\n");
                }
            }
        }
        writeTextToPdf(text.toString().trim(), pdfPath);
    }

    private void writeTextToPdf(String fullText, String pdfPath) throws Exception {
        if (fullText.isEmpty()) { createBlankPdf(pdfPath, "（内容为空）"); return; }
        try (PDDocument pdf = new PDDocument()) {
            String fontPath = findTtfFont();
            PDType0Font font = fontPath != null ? PDType0Font.load(pdf, new File(fontPath)) : null;
            for (String line : fullText.split("\n")) {
                if (line.trim().isEmpty()) continue;
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.beginText();
                    if (font != null) cs.setFont(font, 10);
                    cs.setLeading(16);
                    cs.newLineAtOffset(40, 760);
                    for (int i = 0; i < line.length(); i += 90) {
                        cs.showText(line.substring(i, Math.min(i + 90, line.length())));
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            pdf.save(pdfPath);
        }
    }

    private String findTtfFont() {
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/simsun.ttf", "C:/Windows/Fonts/msyh.ttf"
        };
        for (String path : candidates) {
            if (new File(path).exists()) return path;
        }
        return null;
    }

    private void createBlankPdf(String outputPath, String hint) throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            String fontPath = findTtfFont();
            if (fontPath != null) {
                PDType0Font font = PDType0Font.load(pdf, new File(fontPath));
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.beginText(); cs.setFont(font, 16);
                    cs.newLineAtOffset(100, 500); cs.showText(hint);
                    cs.endText();
                }
            }
            pdf.save(outputPath);
        }
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    @SuppressWarnings("unused")
    private void libreOfficeConvert(String inputPath, String outputPath) throws Exception {
        if (!tryLibreOffice(inputPath, outputPath)) {
            throw new RuntimeException("LibreOffice 转换失败");
        }
    }
}
