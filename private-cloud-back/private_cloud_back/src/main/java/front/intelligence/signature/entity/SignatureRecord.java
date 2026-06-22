package front.intelligence.signature.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signature_record")
public class SignatureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "signer_id", nullable = false)
    private Long signerId;

    @Column(name = "signer_name", nullable = false, length = 100)
    private String signerName;

    @Column(name = "sign_time", nullable = false)
    private LocalDateTime signTime;

    @Column(name = "seal_image_url", length = 500)
    private String sealImageUrl;

    @Column(name = "signature_hash", length = 128)
    private String signatureHash;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getSignerId() { return signerId; }
    public void setSignerId(Long signerId) { this.signerId = signerId; }
    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }
    public LocalDateTime getSignTime() { return signTime; }
    public void setSignTime(LocalDateTime signTime) { this.signTime = signTime; }
    public String getSealImageUrl() { return sealImageUrl; }
    public void setSealImageUrl(String sealImageUrl) { this.sealImageUrl = sealImageUrl; }
    public String getSignatureHash() { return signatureHash; }
    public void setSignatureHash(String signatureHash) { this.signatureHash = signatureHash; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
