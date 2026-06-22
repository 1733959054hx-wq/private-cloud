package front.intelligence.comment.dto;

import java.time.LocalDateTime;

public class CommentDTO {

    private Long id;
    private Long fileId;
    private Long userId;
    private String username;
    private String content;
    private Long parentId;
    private String mentions;
    private Long departmentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean canDelete;
    private Boolean isFileOwner;
    private String quoteText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getMentions() { return mentions; }
    public void setMentions(String mentions) { this.mentions = mentions; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Boolean getCanDelete() { return canDelete; }
    public void setCanDelete(Boolean canDelete) { this.canDelete = canDelete; }
    public Boolean getIsFileOwner() { return isFileOwner; }
    public void setIsFileOwner(Boolean isFileOwner) { this.isFileOwner = isFileOwner; }
    public String getQuoteText() { return quoteText; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }
}
