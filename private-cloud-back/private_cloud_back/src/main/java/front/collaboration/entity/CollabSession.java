package front.collaboration.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collab_session")
public class CollabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "room_name", length = 200)
    private String roomName;

    @Column(name = "status")
    private Integer status = 0;

    @Column(name = "permission_mode", length = 20)
    private String permissionMode = "editable";

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    @Lob
    private String content;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "close_time")
    private LocalDateTime closeTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getPermissionMode() { return permissionMode; }
    public void setPermissionMode(String permissionMode) { this.permissionMode = permissionMode; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }
}
