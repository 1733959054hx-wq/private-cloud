package front.system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_sensitive_word")
public class SysSensitiveWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", length = 100, nullable = false, unique = true)
    private String word;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "level")
    private Integer level;

    @Column(name = "enabled")
    private Integer enabled = 1;

    @Column(name = "create_by")
    private Long createBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        if (this.createTime == null) this.createTime = LocalDateTime.now();
        if (this.updateTime == null) this.updateTime = LocalDateTime.now();
        if (this.enabled == null) this.enabled = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
