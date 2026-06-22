package front.system.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户凭证独立表（与业务信息分离，降低拖库风险）
 * <p>
 * 安全升级：使用 HMAC-SHA256 生成的 linkToken 作为主键，
 * 彻底切断与 sys_user 表的明文 ID 关联。
 * 拖库后凭证表只有无规律的 linkToken 和密码密文，
 * 缺少服务器 secret 时无法对应到具体用户。
 */
@Entity
@Table(name = "sys_user_credential")
public class SysUserCredential {

    @Id
    @Column(name = "link_token", length = 64)
    private String linkToken;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public String getLinkToken() { return linkToken; }
    public void setLinkToken(String linkToken) { this.linkToken = linkToken; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public String getLastLoginIp() { return lastLoginIp; }
    public void setLastLoginIp(String lastLoginIp) { this.lastLoginIp = lastLoginIp; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
