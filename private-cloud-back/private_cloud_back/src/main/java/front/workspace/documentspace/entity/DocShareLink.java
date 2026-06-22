package front.workspace.documentspace.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_share_link")
public class DocShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "max_access")
    private Integer maxAccess = 0;

    @Column(name = "access_count")
    private Integer accessCount = 0;

    @Column(name = "permission_type", length = 20)
    private String permissionType = "view";

    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public Integer getMaxAccess() { return maxAccess; }
    public void setMaxAccess(Integer maxAccess) { this.maxAccess = maxAccess; }
    public Integer getAccessCount() { return accessCount; }
    public void setAccessCount(Integer accessCount) { this.accessCount = accessCount; }
    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
