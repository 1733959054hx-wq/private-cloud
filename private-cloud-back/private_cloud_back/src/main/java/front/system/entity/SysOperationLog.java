package front.system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_operation_log")
public class SysOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户ID（未登录操作为null） */
    @Column(name = "user_id")
    private Long userId;

    /** 操作用户名（冗余存储，便于审计查询） */
    @Column(name = "username", length = 50)
    private String username;

    /** 操作模块：AUTH / USER / FILE / ROLE / SYSTEM */
    @Column(name = "module", length = 30)
    private String module;

    /** 操作类型：LOGIN / LOGOUT / CREATE / UPDATE / DELETE / VIEW / DOWNLOAD / PASSWORD_CHANGE / ROLE_CHANGE */
    @Column(name = "operation", length = 100)
    private String operation;

    /** 操作目标类型：user / file / role / directory 等 */
    @Column(name = "target_type", length = 30)
    private String targetType;

    /** 操作目标ID */
    @Column(name = "target_id")
    private Long targetId;

    /** 操作详情 */
    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    /** 操作结果：success / fail */
    @Column(name = "status", length = 10)
    private String status;

    /** 请求IP */
    @Column(name = "ip", length = 50)
    private String ip;

    /** 请求User-Agent */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
