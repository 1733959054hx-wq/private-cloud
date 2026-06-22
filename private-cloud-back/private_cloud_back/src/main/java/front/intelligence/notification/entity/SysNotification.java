package front.intelligence.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_notification")
public class SysNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id", nullable = false)
    private Long userId;

    @Column(name = "type", length = 30, nullable = false)
    private String type;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "from_user_id")
    private Long fromUserId;

    @Column(name = "from_username", length = 50)
    private String fromUsername;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "is_read", columnDefinition = "INTEGER DEFAULT 0")
    private Integer isRead = 0;

    @Column(name = "read_time")
    private LocalDateTime readTime;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }
    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getRelatedId() { return relatedId; }
    public void setRelatedId(Long relatedId) { this.relatedId = relatedId; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
    public LocalDateTime getReadTime() { return readTime; }
    public void setReadTime(LocalDateTime readTime) { this.readTime = readTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}