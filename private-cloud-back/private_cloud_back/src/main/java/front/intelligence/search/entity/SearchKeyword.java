package front.intelligence.search.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_keyword")
public class SearchKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keyword", nullable = false, unique = true, length = 200)
    private String keyword;

    @Column(name = "search_count")
    private Integer searchCount = 0;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "is_hot")
    private Integer isHot = 0;

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
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getSearchCount() { return searchCount; }
    public void setSearchCount(Integer searchCount) { this.searchCount = searchCount; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getIsHot() { return isHot; }
    public void setIsHot(Integer isHot) { this.isHot = isHot; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
