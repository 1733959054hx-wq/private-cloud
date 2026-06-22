package front.workspace.documentspace.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recycle_bin")
public class RecycleBin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_name", nullable = false, length = 500)
    private String itemName;

    @Column(name = "deleted_by", nullable = false)
    private Long deletedBy;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
