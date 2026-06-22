package front.workspace.documentspace.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_file", indexes = {
    @Index(name = "idx_directory_id", columnList = "directory_id"),
    @Index(name = "idx_department_id", columnList = "department_id"),
    @Index(name = "idx_deleted_status", columnList = "deleted, status"),
    @Index(name = "idx_directory_deleted_status", columnList = "directory_id, deleted, status"),
    @Index(name = "idx_space", columnList = "space_type, space_id, directory_id")
})
public class DocFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "storage_type", length = 20)
    private String storageType = "local";

    @Column(name = "md5", length = 64)
    private String md5;

    @Column(name = "fulltext_content", columnDefinition = "LONGTEXT")
    private String fulltextContent;

    @Column(name = "directory_id")
    private Long directoryId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "space_type")
    private Integer spaceType = 0;

    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "uploader_name", length = 100)
    private String uploaderName;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "download_count")
    private Integer downloadCount = 0;

    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "deleted", columnDefinition = "INTEGER DEFAULT 0")
    private Integer deleted = 0;

    @Column(name = "preview_status", length = 20)
    private String previewStatus = "NOT_STARTED";

    @Column(name = "preview_pdf_path", length = 500)
    private String previewPdfPath;

    @Column(name = "mindmap_content", columnDefinition = "LONGTEXT")
    private String mindmapContent;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

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
    public String getFulltextContent() { return fulltextContent; }
    public void setFulltextContent(String fulltextContent) { this.fulltextContent = fulltextContent; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getPreviewStatus() { return previewStatus; }
    public void setPreviewStatus(String previewStatus) { this.previewStatus = previewStatus; }
    public String getPreviewPdfPath() { return previewPdfPath; }
    public void setPreviewPdfPath(String previewPdfPath) { this.previewPdfPath = previewPdfPath; }
    public String getMindmapContent() { return mindmapContent; }
    public void setMindmapContent(String mindmapContent) { this.mindmapContent = mindmapContent; }
}
