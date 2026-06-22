package front.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "wf_approval_request")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "status")
    private Integer status = 0;

    @Column(name = "current_step")
    private Integer currentStep = 0;

    @Column(name = "stamped_file_id")
    private Long stampedFileId;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

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
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public Long getStampedFileId() { return stampedFileId; }
    public void setStampedFileId(Long stampedFileId) { this.stampedFileId = stampedFileId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
