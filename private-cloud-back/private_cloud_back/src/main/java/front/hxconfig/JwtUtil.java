package front.hxconfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    // 使用固定的实例标识，避免后端重启导致所有已签发 Token 失效
    // 不再依赖 serverInstanceId 做 Token 失效校验，JWT 本身的过期时间已足够
    private static final String SERVER_INSTANCE_ID = "private-cloud-server-v1";

    // Token 黑名单 Redis key 前缀
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("sid", SERVER_INSTANCE_ID);
        // 唯一标识，用于黑名单吊销
        claims.put("jti", UUID.randomUUID().toString());

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            String tokenSid = claims.get("sid", String.class);
            if (tokenSid == null || !tokenSid.equals(SERVER_INSTANCE_ID)) {
                return false;
            }
            // 检查黑名单
            if (isBlacklisted(claims)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将 Token 加入黑名单（登出/改密码时调用）
     * 过期时间设为 Token 剩余有效期，到期自动清理
     */
    public void blacklistToken(String token) {
        if (stringRedisTemplate == null) return;
        try {
            Claims claims = parseToken(token);
            String jti = claims.get("jti", String.class);
            if (jti == null) return;
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs <= 0) return;
            stringRedisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jti, "1", remainingMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Token 已无效，无需加入黑名单
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     */
    private boolean isBlacklisted(Claims claims) {
        if (stringRedisTemplate == null) return false;
        String jti = claims.get("jti", String.class);
        if (jti == null) return false;
        Boolean exists = stringRedisTemplate.hasKey(BLACKLIST_PREFIX + jti);
        return Boolean.TRUE.equals(exists);
    }
}
