package front.workspace.personalworkspace.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 收藏状态缓存服务
 *
 * 缓存结构：
 *   Key:   favorite:status:{userId}:{targetId}:{type}
 *   Value: "1"  (已收藏)
 *   TTL:   30min
 *
 * 设计动机：
 *   1. 文件详情页/列表需要频繁判断「当前用户是否已收藏」该目标
 *   2. 收藏操作本身低频，但读取判断很频繁
 *   3. 用 String 缓存「是否存在」标志，避免每次查 DB
 *   4. 添加/取消收藏时主动更新缓存，保证一致性
 */
@Service
public class FavoriteCacheService {

    private static final String STATUS_KEY_PREFIX = "favorite:status:";
    private static final long STATUS_TTL_MINUTES = 30;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 查询收藏状态（true=已收藏，false=未收藏，null=缓存未命中）
     */
    public Boolean getStatus(Long userId, Long targetId, Integer targetType) {
        try {
            String key = buildKey(userId, targetId, targetType);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            return "1".equals(cached.toString());
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取收藏状态失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 把"已收藏"状态写入缓存
     */
    public void putFavorited(Long userId, Long targetId, Integer targetType) {
        try {
            String key = buildKey(userId, targetId, targetType);
            redisTemplate.opsForValue().set(key, "1", STATUS_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 写入收藏状态失败: " + e.getMessage());
        }
    }

    /**
     * 收藏被取消时删除缓存
     */
    public void evictStatus(Long userId, Long targetId, Integer targetType) {
        try {
            String key = buildKey(userId, targetId, targetType);
            redisTemplate.delete(key);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 清除收藏状态失败: " + e.getMessage());
        }
    }

    /**
     * 切换收藏状态（添加/取消）
     *
     * @return true 写入已收藏；false 删除缓存
     */
    public void applyStatus(Long userId, Long targetId, Integer targetType, boolean favorited) {
        if (favorited) {
            putFavorited(userId, targetId, targetType);
        } else {
            evictStatus(userId, targetId, targetType);
        }
    }

    private String buildKey(Long userId, Long targetId, Integer targetType) {
        return STATUS_KEY_PREFIX + userId + ":" + targetId + ":" + targetType;
    }
}
