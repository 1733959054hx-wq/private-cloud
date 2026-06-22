package front.intelligence.preview.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_progress_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_file_type", columnNames = {"user_id", "file_id", "progress_type"})
})
public class DocProgressRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "progress_type", nullable = false)
    private Integer progressType; // 1-文档页码，2-视频秒数

    @Column(name = "progress_value", nullable = false)
    private Double progressValue;

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
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Integer getProgressType() { return progressType; }
    public void setProgressType(Integer progressType) { this.progressType = progressType; }
    public Double getProgressValue() { return progressValue; }
    public void setProgressValue(Double progressValue) { this.progressValue = progressValue; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
