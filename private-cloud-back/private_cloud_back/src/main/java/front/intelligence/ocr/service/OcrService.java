package front.intelligence.ocr.service;

import front.intelligence.ocr.entity.OcrRecord;
import front.intelligence.ocr.repository.OcrRecordRepository;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import okhttp3.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OcrService {

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private OcrRecordRepository ocrRecordRepository;

    @Autowired
    private front.storage.service.StorageRouter storageRouter;

    @Autowired
    private front.storage.service.StorageHelper storageHelper;

    @Value("${ocr.api-key:}")
    private String apiKey;

    @Value("${ocr.secret-key:}")
    private String secretKey;

    @Value("${ocr.provider:baidu}")
    private String ocrProvider;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private String accessToken;

    @Transactional
    public OcrRecord prepareOcrRecord(Long fileId, Integer pageNumber) {
        if (pageNumber != null) {
            List<OcrRecord> existing = ocrRecordRepository.findAllByFileIdAndPageNumber(fileId, pageNumber);
            if (existing.size() > 1) {
                OcrRecord keep = existing.get(0);
                for (int i = 1; i < existing.size(); i++) {
                    ocrRecordRepository.delete(existing.get(i));
                }
                if (keep.getStatus() == 1) {
                    keep.setStatus(0);
                }
                return ocrRecordRepository.save(keep);
            }
            if (existing.size() == 1) {
                OcrRecord record = existing.get(0);
                if (record.getStatus() == 1) {
                    record.setStatus(0);
                    ocrRecordRepository.save(record);
                }
                return record;
            }
            OcrRecord newRecord = new OcrRecord();
            newRecord.setFileId(fileId);
            newRecord.setPageNumber(pageNumber);
            newRecord.setStatus(0);
            return ocrRecordRepository.save(newRecord);
        } else {
            List<OcrRecord> existing = ocrRecordRepository.findAllByFileId(fileId);
            if (existing.size() > 1) {
                OcrRecord keep = existing.get(0);
                for (int i = 1; i < existing.size(); i++) {
                    ocrRecordRepository.delete(existing.get(i));
                }
                if (keep.getStatus() == 1) {
                    keep.setStatus(0);
                }
                return ocrRecordRepository.save(keep);
            }
            if (existing.size() == 1) {
                OcrRecord record = existing.get(0);
                if (record.getStatus() == 1) {
                    record.setStatus(0);
                    ocrRecordRepository.save(record);
                }
                return record;
            }
            OcrRecord newRecord = new OcrRecord();
            newRecord.setFileId(fileId);
            newRecord.setPageNumber(null);
            newRecord.setStatus(0);
            return ocrRecordRepository.save(newRecord);
        }
    }

    @Async("ocrExecutor")
    @Transactional
    public void triggerOcrAsync(Long fileId) {
        triggerOcrAsync(fileId, null);
    }

    @Async("ocrExecutor")
    @Transactional
    public void triggerOcrAsync(Long fileId, Integer pageNumber) {
        OcrRecord record;
        if (pageNumber != null) {
            record = ocrRecordRepository.findByFileIdAndPageNumber(fileId, pageNumber)
                    .orElseGet(() -> {
                        OcrRecord newRecord = new OcrRecord();
                        newRecord.setFileId(fileId);
                        newRecord.setPageNumber(pageNumber);
                        newRecord.setStatus(0);
                        return newRecord;
                    });
        } else {
            record = ocrRecordRepository.findByFileId(fileId)
                    .orElseGet(() -> {
                        OcrRecord newRecord = new OcrRecord();
                        newRecord.setFileId(fileId);
                        newRecord.setPageNumber(null);
                        newRecord.setStatus(0);
                        return newRecord;
                    });
        }

        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                record.setStatus(1);
                ocrRecordRepository.save(record);

                DocFile file = fileRepository.findById(fileId)
                        .orElseThrow(() -> new RuntimeException("OCR: 文件不存在, fileId=" + fileId));

                String ocrText;
                if (pageNumber != null) {
                    ocrText = performOcrByPage(file, pageNumber);
                } else {
                    ocrText = performOcr(file);
                }

                record.setStatus(2);
                record.setOcrText(ocrText);
                ocrRecordRepository.save(record);

                if (ocrText != null && !ocrText.isEmpty()) {
                    String existing = file.getFulltextContent();
                    if (existing != null && !existing.isEmpty()) {
                        file.setFulltextContent(existing + "\n" + ocrText);
                    } else {
                        file.setFulltextContent(ocrText);
                    }
                    fileRepository.save(file);
                }
                
                return;
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                System.err.println("OCR处理失败, fileId=" + fileId + ", page=" + pageNumber + ", error=" + e.getMessage() + 
                        ", retryCount=" + retryCount + "/" + maxRetries);
                
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        record.setStatus(3);
        record.setErrorMessage(lastException != null ? lastException.getMessage() : "OCR处理失败");
        ocrRecordRepository.save(record);
    }

    public OcrRecord getOcrRecord(Long fileId) {
        return ocrRecordRepository.findByFileId(fileId).orElse(null);
    }

    public OcrRecord getOcrRecordByPage(Long fileId, Integer pageNumber) {
        return ocrRecordRepository.findByFileIdAndPageNumber(fileId, pageNumber).orElse(null);
    }

    public String performOcr(DocFile file) {
        String ext = file.getFileType();
        if (ext == null) {
            return "";
        }

        String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
        String filePath = file.getFilePath();

        // MinIO 存储：下载到本地临时文件后 OCR
        if ("minio".equalsIgnoreCase(storageType)) {
            String tempSuffix = "." + (ext.isEmpty() ? "tmp" : ext.toLowerCase());
            java.nio.file.Path tempFile = null;
            try {
                tempFile = storageHelper.ensureLocalAccessible(storageType, filePath, "ocr_", tempSuffix);
                return callBaiduOcrApi(tempFile.toFile().getAbsolutePath());
            } catch (Exception e) {
                throw new RuntimeException("从 MinIO 下载文件失败: " + filePath, e);
            } finally {
                if (tempFile != null) {
                    storageHelper.cleanupTempFile(tempFile);
                }
            }
        }

        if (isImageType(ext)) {
            return callBaiduOcrApi(filePath);
        }

        if ("pdf".equalsIgnoreCase(ext)) {
            return callBaiduOcrApi(filePath);
        }

        return "";
    }

    /** 按页OCR：将PDF/PPT/Word的指定页渲染为图片后OCR */
    public String performOcrByPage(DocFile file, int pageNumber) {
        String ext = file.getFileType();
        if (ext == null) {
            return "";
        }

        String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
        String filePath = file.getFilePath();

        // 图片类型直接OCR
        if (isImageType(ext)) {
            if ("minio".equalsIgnoreCase(storageType)) {
                String tempSuffix = "." + ext.toLowerCase();
                java.nio.file.Path tempFile = null;
                try {
                    tempFile = storageHelper.ensureLocalAccessible(storageType, filePath, "ocr_page_", tempSuffix);
                    return callBaiduOcrApi(tempFile.toFile().getAbsolutePath());
                } catch (Exception e) {
                    throw new RuntimeException("从 MinIO 下载文件失败: " + filePath, e);
                } finally {
                    if (tempFile != null) {
                        storageHelper.cleanupTempFile(tempFile);
                    }
                }
            }
            return callBaiduOcrApi(filePath);
        }

        // PDF类型：用PDFBox渲染指定页为图片后OCR
        if ("pdf".equalsIgnoreCase(ext)) {
            if ("minio".equalsIgnoreCase(storageType)) {
                java.nio.file.Path tempFile = null;
                try {
                    tempFile = storageHelper.ensureLocalAccessible(storageType, filePath, "ocr_pdf_", ".pdf");
                    return ocrPdfPage(tempFile.toFile().getAbsolutePath(), pageNumber);
                } catch (Exception e) {
                    throw new RuntimeException("从 MinIO 下载文件失败: " + filePath, e);
                } finally {
                    if (tempFile != null) {
                        storageHelper.cleanupTempFile(tempFile);
                    }
                }
            }
            return ocrPdfPage(filePath, pageNumber);
        }

        // Word/PPT类型：先找转换后的PDF缓存，再渲染指定页OCR
        if (isDocOrPptType(ext)) {
            String convertedPdfPath = file.getPreviewPdfPath();
            if (convertedPdfPath != null && !convertedPdfPath.isEmpty()) {
                if ("minio".equalsIgnoreCase(storageType)) {
                    java.nio.file.Path tempFile = null;
                    try {
                        tempFile = storageHelper.ensureLocalAccessible(storageType, convertedPdfPath, "ocr_docpdf_", ".pdf");
                        File pdfFile = tempFile.toFile();
                        if (pdfFile.exists()) {
                            return ocrPdfPage(pdfFile.getAbsolutePath(), pageNumber);
                        }
                    } catch (Exception e) {
                        return "从 MinIO 下载转换后 PDF 失败: " + e.getMessage();
                    } finally {
                        if (tempFile != null) {
                            storageHelper.cleanupTempFile(tempFile);
                        }
                    }
                } else {
                    File pdfFile = resolveFile(convertedPdfPath);
                    if (pdfFile.exists()) {
                        return ocrPdfPage(pdfFile.getAbsolutePath(), pageNumber);
                    }
                }
            }
            return "该文件尚未完成PDF转换，请先预览文件后再进行OCR识别";
        }

        return "";
    }

    private boolean isDocOrPptType(String ext) {
        return ext.equalsIgnoreCase("docx") || ext.equalsIgnoreCase("doc")
                || ext.equalsIgnoreCase("pptx") || ext.equalsIgnoreCase("ppt");
    }

    /** 将PDF指定页渲染为图片后调用百度OCR */
    private String ocrPdfPage(String filePath, int pageNumber) {
        try {
            File file = resolveFile(filePath);
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + file.getAbsolutePath());
            }

            try (PDDocument doc = Loader.loadPDF(file)) {
                if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                    throw new RuntimeException("页码超出范围: " + pageNumber + ", 总页数: " + doc.getNumberOfPages());
                }

                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, 150);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "jpeg", baos);
                byte[] imageBytes = baos.toByteArray();

                return callBaiduOcrApiWithBytes(imageBytes);
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF页面OCR失败: " + e.getMessage(), e);
        }
    }

    private File resolveFile(String filePath) {
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            file = new File(uploadDir, filePath);
            if (!file.exists()) {
                file = new File(filePath);
            }
        }
        return file;
    }

    private boolean isImageType(String ext) {
        return ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg")
                || ext.equalsIgnoreCase("png") || ext.equalsIgnoreCase("bmp")
                || ext.equalsIgnoreCase("tiff") || ext.equalsIgnoreCase("gif");
    }

    private String callBaiduOcrApi(String filePath) {
        try {
            if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
                throw new RuntimeException("百度OCR API密钥未配置");
            }

            if (accessToken == null || accessToken.isEmpty()) {
                accessToken = getAccessToken();
            }

            File file = resolveFile(filePath);
            
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + file.getAbsolutePath());
            }

            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            
            if (fileBytes.length > 4 * 1024 * 1024) {
                throw new RuntimeException("文件大小超过4MB限制: " + fileBytes.length + " bytes");
            }
            
            String imageBase64 = Base64.getEncoder().encodeToString(fileBytes);
            imageBase64 = imageBase64.replaceAll("\\s+", "");

            return callBaiduOcrApiWithBase64(imageBase64);
        } catch (IOException e) {
            throw new RuntimeException("百度OCR API调用异常: " + e.getMessage(), e);
        }
    }

    /** 用字节数组调用百度OCR（用于PDF渲染后的图片） */
    private String callBaiduOcrApiWithBytes(byte[] imageBytes) {
        try {
            if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
                throw new RuntimeException("百度OCR API密钥未配置");
            }

            if (accessToken == null || accessToken.isEmpty()) {
                accessToken = getAccessToken();
            }

            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            imageBase64 = imageBase64.replaceAll("\\s+", "");

            return callBaiduOcrApiWithBase64(imageBase64);
        } catch (Exception e) {
            throw new RuntimeException("百度OCR API调用异常: " + e.getMessage(), e);
        }
    }

    private String callBaiduOcrApiWithBase64(String imageBase64) throws IOException {
        String url = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";

        RequestBody requestBody = new FormBody.Builder()
                .add("image", imageBase64)
                .build();

        Request request = new Request.Builder()
                .url(url + "?access_token=" + accessToken)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("百度OCR API调用失败: " + response.code());
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = JSON.parseObject(responseBody);

            if (jsonResponse.containsKey("error_code")) {
                String errorCode = jsonResponse.getString("error_code");
                String errorMsg = jsonResponse.getString("error_msg");
                
                if ("110".equals(errorCode) || "111".equals(errorCode)) {
                    accessToken = getAccessToken();
                    return callBaiduOcrApiWithBase64(imageBase64);
                }
                
                throw new RuntimeException("百度OCR API错误: " + errorCode + " - " + errorMsg);
            }

            StringBuilder result = new StringBuilder();
            if (jsonResponse.containsKey("words_result")) {
                var wordsResult = jsonResponse.getJSONArray("words_result");
                for (int i = 0; i < wordsResult.size(); i++) {
                    JSONObject word = wordsResult.getJSONObject(i);
                    result.append(word.getString("words"));
                    result.append("\n");
                }
            }

            return result.toString().trim();
        }
    }

    private String getAccessToken() throws IOException {
        String url = "https://aip.baidubce.com/oauth/2.0/token?" +
                "grant_type=client_credentials" +
                "&client_id=" + apiKey +
                "&client_secret=" + secretKey;

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0]))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("获取百度OCR access_token失败: " + response.code());
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = JSON.parseObject(responseBody);

            if (jsonResponse.containsKey("access_token")) {
                return jsonResponse.getString("access_token");
            } else {
                throw new RuntimeException("获取百度OCR access_token失败: " + responseBody);
            }
        }
    }
}
