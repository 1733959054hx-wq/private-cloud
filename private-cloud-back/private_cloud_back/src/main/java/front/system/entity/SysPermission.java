package front.system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_permission")
public class SysPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "type")
    private Integer type;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
