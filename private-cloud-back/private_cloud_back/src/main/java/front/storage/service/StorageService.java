package front.storage.service;

import java.io.InputStream;
import java.time.Duration;

/**
 * 文件存储抽象接口
 * 支持 LocalStorageService（本地磁盘）与 MinioStorageService（MinIO 对象存储）两种实现
 */
public interface StorageService {

    /**
     * 上传文件
     *
     * @param inputStream  文件输入流
     * @param objectName   存储对象名（本地为绝对路径，MinIO 为对象 key）
     * @param size         文件大小（字节），-1 表示未知
     * @param contentType  MIME 类型
     * @return 实际存储路径（本地为绝对路径，MinIO 为对象名）
     */
    String upload(InputStream inputStream, String objectName, long size, String contentType) throws Exception;

    /**
     * 下载文件为输入流
     */
    InputStream download(String objectName) throws Exception;

    /**
     * 删除文件
     */
    void delete(String objectName) throws Exception;

    /**
     * 判断文件是否存在
     */
    boolean exists(String objectName);

    /**
     * 获取预签名下载 URL（本地存储返回 null，表示不支持）
     *
     * @param objectName 对象名
     * @param expiry     有效期
     * @return 预签名 URL，或 null 表示不支持
     */
    String getPresignedUrl(String objectName, Duration expiry);

    /**
     * 复制对象（同存储类型内部复制）
     *
     * @param sourceObjectName 源对象名
     * @param targetObjectName 目标对象名
     */
    void copy(String sourceObjectName, String targetObjectName) throws Exception;

    /**
     * 返回存储类型标识："local" 或 "minio"
     */
    String getStorageType();
}
