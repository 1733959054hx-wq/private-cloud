package front.workspace.documentspace.dto;

import java.time.LocalDateTime;

public class FileDTO {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String storageType;
    private String md5;
    private Long directoryId;
    private String directoryName;
    private Long departmentId;
    private Integer spaceType;
    private Long spaceId;
    private Long uploaderId;
    private String uploaderName;
    private Integer version;
    private Integer viewCount;
    private Integer downloadCount;
    private Integer status;
    private Boolean isFavorited;
    private String previewStatus;
    private String previewPdfPath;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    public Long getDirectoryId() { return directoryId; }
    public void setDirectoryId(Long directoryId) { this.directoryId = directoryId; }
    public String getDirectoryName() { return directoryName; }
    public void setDirectoryName(String directoryName) { this.directoryName = directoryName; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Integer getSpaceType() { return spaceType; }
    public void setSpaceType(Integer spaceType) { this.spaceType = spaceType; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getDownloadCount() { return downloadCount; }
    public void setDownloadCount(Integer downloadCount) { this.downloadCount = downloadCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getIsFavorited() { return isFavorited; }
    public void setIsFavorited(Boolean isFavorited) { this.isFavorited = isFavorited; }
    public String getPreviewStatus() { return previewStatus; }
    public void setPreviewStatus(String previewStatus) { this.previewStatus = previewStatus; }
    public String getPreviewPdfPath() { return previewPdfPath; }
    public void setPreviewPdfPath(String previewPdfPath) { this.previewPdfPath = previewPdfPath; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
