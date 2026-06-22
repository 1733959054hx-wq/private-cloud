package front.hxconfig;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.security.*;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSA 密钥对管理器
 * - 应用启动时生成 RSA 2048 位密钥对
 * - 支持按会话ID绑定密钥（一次性使用）
 * - 提供公钥获取和私钥解密能力
 */
@Component
public class RsaKeyManager {

    /** 全局默认密钥对（用于登录密码加密传输） */
    private KeyPair globalKeyPair;

    /** 一次性会话密钥对缓存：sessionId -> KeyPair */
    private final Map<String, KeyPair> sessionKeyPairs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.globalKeyPair = generateKeyPair();
    }

    /**
     * 生成 RSA 2048 位密钥对
     */
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA算法不可用", e);
        }
    }

    /**
     * 获取全局公钥（Base64编码）
     */
    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(globalKeyPair.getPublic().getEncoded());
    }

    /**
     * 使用全局私钥解密 Base64 编码的密文
     */
    public String decrypt(String encryptedBase64) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, globalKeyPair.getPrivate());
            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA解密失败", e);
        }
    }

    /**
     * 生成一次性会话密钥对，返回 sessionId
     */
    public String createSessionKeyPair() {
        String sessionId = UUID.randomUUID().toString();
        sessionKeyPairs.put(sessionId, generateKeyPair());
        return sessionId;
    }

    /**
     * 获取会话公钥（Base64编码）
     */
    public String getSessionPublicKeyBase64(String sessionId) {
        KeyPair kp = sessionKeyPairs.get(sessionId);
        if (kp == null) {
            throw new RuntimeException("会话密钥对不存在或已过期");
        }
        return Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    }

    /**
     * 使用会话私钥解密，并销毁该会话密钥（一次性使用）
     */
    public String decryptWithSession(String sessionId, String encryptedBase64) {
        KeyPair kp = sessionKeyPairs.remove(sessionId);
        if (kp == null) {
            throw new RuntimeException("会话密钥对不存在或已使用");
        }
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, kp.getPrivate());
            byte[] decrypted = cipher.doFinal(encryptedBytes);
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("RSA会话解密失败", e);
        }
    }

    /**
     * 清理过期的会话密钥对（可由定时任务调用）
     */
    public void cleanExpiredSessions() {
        // 简单实现：当缓存超过1000个时清空全部（生产环境应配合TTL）
        if (sessionKeyPairs.size() > 1000) {
            sessionKeyPairs.clear();
        }
    }
}
