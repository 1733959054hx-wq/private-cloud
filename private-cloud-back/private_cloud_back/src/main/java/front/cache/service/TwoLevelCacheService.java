package front.cache.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * 二级缓存抽象服务
 *
 * 查询路径：L1（Caffeine）→ L2（Redis）→ DB（由 loader 提供）
 * 写入路径：DB 写入后 → 清 L1 → 清 L2（或更新 L2）
 * 失效路径：清 L1 → 清 L2
 *
 * 设计要点：
 * 1. L1 命中率最高，访问延迟最低（纳秒级）
 * 2. L2 跨实例共享，避免缓存击穿后所有实例同时回源
 * 3. L1 TTL 较短（秒级），L2 TTL 较长（分钟级），保证最终一致性
 * 4. 任何一层异常都不影响业务，降级到下一层
 */
@Service
public class TwoLevelCacheService {

    private static final Logger log = LoggerFactory.getLogger(TwoLevelCacheService.class);

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 暴露 CacheManager，供需要直接操作 L1 的服务使用
     */
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * 查询缓存
     *
     * @param cacheName  Caffeine 缓存名
     * @param redisKey   Redis 缓存键
     * @param loader     DB 加载函数（缓存未命中时调用）
     * @param redisTtl   Redis 缓存 TTL
     * @return 缓存值
     */
    public <T> T get(String cacheName, String redisKey, Callable<T> loader, Duration redisTtl) {
        // 1. L1：Caffeine
        T value = getFromL1(cacheName, redisKey);
        if (value != null) {
            log.info("[二级缓存] L1(Caffeine)命中, cache={}, key={}", cacheName, redisKey);
            return value;
        }

        // 2. L2：Redis
        value = getFromL2(redisKey);
        if (value != null) {
            // 回填 L1
            log.info("[二级缓存] L2(Redis)命中并回填L1, cache={}, key={}", cacheName, redisKey);
            putToL1(cacheName, redisKey, value);
            return value;
        }

        // 3. DB：loader
        log.info("[二级缓存] 缓存未命中，回源DB, cache={}, key={}", cacheName, redisKey);
        try {
            value = loader.call();
        } catch (Exception e) {
            log.error("[二级缓存] DB 加载失败, redisKey={}, error={}", redisKey, e.getMessage(), e);
            throw new RuntimeException("缓存加载失败", e);
        }

        // 回填 L2 和 L1
        if (value != null) {
            putToL2(redisKey, value, redisTtl);
            putToL1(cacheName, redisKey, value);
        }
        return value;
    }

    /**
     * 写入缓存（同时更新 L1 和 L2）
     */
    public <T> void put(String cacheName, String redisKey, T value, Duration redisTtl) {
        putToL1(cacheName, redisKey, value);
        putToL2(redisKey, value, redisTtl);
    }

    /**
     * 失效缓存（清 L1 和 L2）
     */
    public void evict(String cacheName, String redisKey) {
        evictL1(cacheName, redisKey);
        evictL2(redisKey);
    }

    /**
     * 批量失效（按 Redis key 模式清除，同时清空整个 Caffeine 缓存）
     *
     * @param cacheName      Caffeine 缓存名
     * @param redisKeyPattern Redis key 模式（如 "file:list:dir:*"）
     */
    public void evictBatch(String cacheName, String redisKeyPattern) {
        // 清空整个 Caffeine 缓存（简单粗暴，但保证一致性）
        evictAllL1(cacheName);
        // 按模式删除 Redis 键
        evictBatchL2(redisKeyPattern);
    }

    // ============ L1 操作 ============

    @SuppressWarnings("unchecked")
    private <T> T getFromL1(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) return null;
            return (T) cache.get(key, () -> null);
        } catch (Exception e) {
            log.warn("[L1] 读取失败, cache={}, key={}, error={}", cacheName, key, e.getMessage());
            return null;
        }
    }

    private <T> void putToL1(String cacheName, String key, T value) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
            }
        } catch (Exception e) {
            log.warn("[L1] 写入失败, cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }

    private void evictL1(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            log.warn("[L1] 失效失败, cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }

    private void evictAllL1(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("[L1] 清空失败, cache={}, error={}", cacheName, e.getMessage());
        }
    }

    // ============ L2 操作 ============

    @SuppressWarnings("unchecked")
    private <T> T getFromL2(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("[L2] 读取失败, key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    private <T> void putToL2(String key, T value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            log.warn("[L2] 写入失败, key={}, error={}", key, e.getMessage());
        }
    }

    private void evictL2(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("[L2] 失效失败, key={}, error={}", key, e.getMessage());
        }
    }

    private void evictBatchL2(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (DataAccessException e) {
            log.warn("[L2] 批量失效失败, pattern={}, error={}", pattern, e.getMessage());
        }
    }
}
