package front.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 本地磁盘存储实现
 * 兼容现有本地文件存储逻辑：objectName 即为本地绝对路径
 */
@Service("localStorageService")
public class LocalStorageService implements StorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public String upload(InputStream inputStream, String objectName, long size, String contentType) throws Exception {
        Path targetPath = Paths.get(objectName);
        Files.createDirectories(targetPath.getParent());
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toString();
    }

    @Override
    public InputStream download(String objectName) throws Exception {
        File file = new File(objectName);
        if (!file.exists()) {
            throw new java.io.FileNotFoundException("本地文件不存在: " + objectName);
        }
        return new FileInputStream(file);
    }

    @Override
    public void delete(String objectName) throws Exception {
        File file = new File(objectName);
        if (file.exists()) {
            if (!file.delete()) {
                // 删除失败不抛异常，仅打印日志，避免影响业务流程
                System.err.println("[本地存储] 文件删除失败: " + objectName);
            }
        }
    }

    @Override
    public boolean exists(String objectName) {
        return new File(objectName).exists();
    }

    @Override
    public String getPresignedUrl(String objectName, Duration expiry) {
        // 本地存储不支持预签名 URL
        return null;
    }

    @Override
    public void copy(String sourceObjectName, String targetObjectName) throws Exception {
        Path source = Paths.get(sourceObjectName);
        Path target = Paths.get(targetObjectName);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public String getStorageType() {
        return "local";
    }
}
