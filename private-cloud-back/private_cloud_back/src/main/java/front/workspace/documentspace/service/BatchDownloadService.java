package front.workspace.documentspace.service;

import front.storage.service.StorageRouter;
import front.workspace.documentspace.dto.FileDTO;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BatchDownloadService {

    private static final Logger log = LoggerFactory.getLogger(BatchDownloadService.class);

    @Value("${file.temp-dir:./temp}")
    private String tempDir;

    @Value("${file.batch-download-expire-hours:24}")
    private int expireHours;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private StorageRouter storageRouter;

    public Map<String, Object> createBatchDownload(List<Long> fileIds) throws IOException {
        List<DocFile> files = fileRepository.findAllById(fileIds);
        if (files.isEmpty()) {
            throw new RuntimeException("没有找到指定文件");
        }

        String zipFileName = "batch_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
        Path zipPath = Paths.get(tempDir, "downloads", zipFileName);
        Files.createDirectories(zipPath.getParent());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            for (DocFile file : files) {
                String storageType = file.getStorageType() != null ? file.getStorageType() : "local";
                String filePath = file.getFilePath();

                if ("minio".equalsIgnoreCase(storageType)) {
                    // MinIO 存储：从 MinIO 下载后写入 zip
                    try (InputStream is = storageRouter.download(storageType, filePath)) {
                        ZipEntry entry = new ZipEntry(file.getFileName());
                        zos.putNextEntry(entry);
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                    } catch (Exception e) {
                        log.warn("[批量下载] 从 MinIO 下载文件失败, fileId={}, path={}, error={}",
                                file.getId(), filePath, e.getMessage());
                    }
                } else {
                    // 本地存储：保持原有逻辑
                    File sourceFile = new File(filePath);
                    if (sourceFile.exists()) {
                        ZipEntry entry = new ZipEntry(file.getFileName());
                        zos.putNextEntry(entry);
                        try (FileInputStream fis = new FileInputStream(sourceFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = fis.read(buffer)) != -1) {
                                zos.write(buffer, 0, len);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("downloadUrl", "/api/front/files/batch-download/" + zipFileName);
        result.put("fileName", zipFileName);
        result.put("fileCount", files.size());
        result.put("expireHours", expireHours);
        return result;
    }

    public File getBatchDownloadFile(String fileName) {
        File file = Paths.get(tempDir, "downloads", fileName).toFile();
        if (!file.exists()) {
            throw new RuntimeException("下载文件不存在或已过期");
        }
        return file;
    }
}
