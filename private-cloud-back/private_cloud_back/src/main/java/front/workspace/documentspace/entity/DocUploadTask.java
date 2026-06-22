package front.workspace.documentspace.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_upload_task")
public class DocUploadTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false, length = 64)
    private String fileId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks;

    @Column(name = "received_chunks")
    private Integer receivedChunks = 0;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "status")
    private Integer status = 0;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "directory_id")
    private Long directoryId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "mode", length = 20)
    private String mode;

    @Column(name = "update_file_id")
    private Long updateFileId;

    @Column(name = "space_type")
    private Integer spaceType = 0;

    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

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
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
    public Integer getReceivedChunks() { return receivedChunks; }
    public void setReceivedChunks(Integer receivedChunks) { this.receivedChunks = receivedChunks; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public Long getDirectoryId() { return directoryId; }
    public void setDirectoryId(Long directoryId) { this.directoryId = directoryId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Long getUpdateFileId() { return updateFileId; }
    public void setUpdateFileId(Long updateFileId) { this.updateFileId = updateFileId; }
    public Integer getSpaceType() { return spaceType; }
    public void setSpaceType(Integer spaceType) { this.spaceType = spaceType; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
