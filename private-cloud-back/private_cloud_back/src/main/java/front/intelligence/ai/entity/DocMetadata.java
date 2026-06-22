package front.intelligence.ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_metadata")
public class DocMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "tag_key", nullable = false, length = 100)
    private String tagKey;

    @Column(name = "tag_value", length = 500)
    private String tagValue;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "source_model", length = 50)
    private String sourceModel;

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
    public String getTagKey() { return tagKey; }
    public void setTagKey(String tagKey) { this.tagKey = tagKey; }
    public String getTagValue() { return tagValue; }
    public void setTagValue(String tagValue) { this.tagValue = tagValue; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getSourceModel() { return sourceModel; }
    public void setSourceModel(String sourceModel) { this.sourceModel = sourceModel; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
