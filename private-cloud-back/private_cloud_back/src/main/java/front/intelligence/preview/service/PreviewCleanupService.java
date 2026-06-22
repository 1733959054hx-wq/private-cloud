package front.intelligence.preview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 定时清理任务：删除磁盘上超过 30 天未被访问的临时 PDF 预览文件和 Excel 缓存文件，
 * 防止磁盘撑爆。
 */
@Service
public class PreviewCleanupService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /** 文件最大保留天数 */
    private static final long MAX_AGE_DAYS = 30;

    /**
     * 每天凌晨 3:00 执行清理任务
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanStalePreviewFiles() {
        long cutoffEpoch = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_AGE_DAYS);

        int pdfCleaned = cleanDirectory(Paths.get(uploadDir, "pdf_cache"), cutoffEpoch);
        int excelCleaned = cleanDirectory(Paths.get(uploadDir, "excel_cache"), cutoffEpoch);

        if (pdfCleaned > 0 || excelCleaned > 0) {
            System.out.println("[预览清理] 清理完成: pdf_cache=" + pdfCleaned + ", excel_cache=" + excelCleaned);
        }
    }

    /**
     * 清理指定目录下最后修改时间早于 cutoffEpoch 的文件
     * 使用 Paths.get 拼接路径，兼容 Windows 和 Linux
     */
    private int cleanDirectory(Path dir, long cutoffEpoch) {
        if (!Files.isDirectory(dir)) return 0;

        int cleaned = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            cleaned = (int) stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            long lastModified = Files.getLastModifiedTime(path).toMillis();
                            return lastModified > 0 && lastModified < cutoffEpoch;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .peek(path -> {
                        try {
                            long lastModified = Files.getLastModifiedTime(path).toMillis();
                            Files.deleteIfExists(path);
                            LocalDateTime fileTime = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(lastModified), ZoneId.systemDefault());
                            System.out.println("[预览清理] 删除过期文件: " + path.getFileName()
                                    + " (最后修改: " + fileTime + ")");
                        } catch (Exception e) {
                            System.err.println("[预览清理] 删除失败: " + path + ", error=" + e.getMessage());
                        }
                    })
                    .count();
        } catch (Exception e) {
            System.err.println("[预览清理] 扫描目录失败: " + dir + ", error=" + e.getMessage());
        }
        return cleaned;
    }
}
