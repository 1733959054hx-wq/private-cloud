package front.hxconfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 盲索引去关联化安全工具类
 * <p>
 * 使用 HMAC-SHA256 算法将用户ID转换为不可逆的 linkToken，
 * 切断 sys_user 表与 sys_user_credential 表的明文ID关联。
 * <p>
 * 安全特性：
 * 1. 拖库后凭证表只有无规律的 linkToken 和密码密文
 * 2. 缺少服务器运行内存中的 blind-index-secret 时，物理上无法将密码对应到具体用户
 * 3. HMAC-SHA256 输出固定 64 字符（Hex编码），满足 VARCHAR(64) 存储
 */
@Component
public class SecurityHashUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${app.security.blind-index-secret}")
    private String blindIndexSecret;

    /**
     * 根据用户ID计算 HMAC-SHA256 linkToken
     *
     * @param userId 用户ID
     * @return 64字符的十六进制 linkToken
     */
    public String computeLinkToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return computeLinkToken(String.valueOf(userId));
    }

    /**
     * 根据原始字符串计算 HMAC-SHA256 linkToken
     *
     * @param input 原始输入（如用户ID的字符串形式）
     * @return 64字符的十六进制 linkToken
     */
    public String computeLinkToken(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("输入不能为空");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    blindIndexSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("计算 linkToken 失败", e);
        }
    }

    /**
     * 字节数组转十六进制字符串（小写）
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
