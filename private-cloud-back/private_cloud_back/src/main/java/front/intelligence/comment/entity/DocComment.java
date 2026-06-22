package front.intelligence.comment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_comment")
public class DocComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "mentions", length = 500)
    private String mentions;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "delete_reason", length = 500)
    private String deleteReason;

    @Column(name = "quote_text", columnDefinition = "TEXT")
    private String quoteText;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "deleted", columnDefinition = "INTEGER DEFAULT 0")
    private Integer deleted = 0;

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
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getMentions() { return mentions; }
    public void setMentions(String mentions) { this.mentions = mentions; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public String getDeleteReason() { return deleteReason; }
    public void setDeleteReason(String deleteReason) { this.deleteReason = deleteReason; }
    public String getQuoteText() { return quoteText; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }
}
