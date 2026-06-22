package front.intelligence.ai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "generated_doc")
public class GeneratedDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "template_id", length = 100)
    private String templateId;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "creator_name", length = 100)
    private String creatorName;

    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "fail_reason", length = 1000)
    private String failReason;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
