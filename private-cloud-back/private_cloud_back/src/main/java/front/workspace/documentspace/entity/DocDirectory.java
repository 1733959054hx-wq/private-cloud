package front.workspace.documentspace.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_directory", indexes = {
    @Index(name = "idx_dir_space", columnList = "space_type, space_id, parent_id")
})
public class DocDirectory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dir_name", nullable = false, length = 200)
    private String dirName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "space_type")
    private Integer spaceType = 0;

    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "sort_order")
    private Integer sortOrder;

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
    public String getDirName() { return dirName; }
    public void setDirName(String dirName) { this.dirName = dirName; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Integer getSpaceType() { return spaceType; }
    public void setSpaceType(Integer spaceType) { this.spaceType = spaceType; }
    public Long getSpaceId() { return spaceId; }
    public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
