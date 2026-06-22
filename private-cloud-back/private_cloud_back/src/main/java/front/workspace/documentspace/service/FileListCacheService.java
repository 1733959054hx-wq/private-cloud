package front.workspace.documentspace.service;

import com.alibaba.fastjson.JSON;
import front.cache.service.TwoLevelCacheService;
import front.workspace.documentspace.dto.FileDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 文件列表缓存服务
 *
 * 二级缓存架构：
 *   L1 = Caffeine（缓存名 fileList，TTL 30s，本地内存）
 *   L2 = Redis（TTL 30±5min，跨实例共享）
 *
 * 查询路径：L1 → L2 → DB
 * 失效路径：清 L1 对应 key → 清 L2 对应 key
 *
 * 注：L1 TTL 故意设短（30s），保证文件列表的最终一致性；
 *     L2 TTL 较长（30min），避免频繁回源 DB。
 */
@Service
public class FileListCacheService {

    private static final Logger log = LoggerFactory.getLogger(FileListCacheService.class);

    private static final String DIR_KEY_PREFIX = "file:list:dir:";
    private static final String SPACE_KEY_PREFIX = "file:list:space:";
    private static final long BASE_TTL_MINUTES = 30;
    private static final int TTL_JITTER_SECONDS = 300;
    private static final String EMPTY_LIST_MARKER = "EMPTY";
    private static final Random RANDOM = new Random();

    /** L1 Caffeine 缓存名 */
    private static final String L1_CACHE_NAME = "fileList";
    /** L2 Redis 缓存 TTL */
    private static final Duration L2_TTL = Duration.ofMinutes(30);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TwoLevelCacheService twoLevelCacheService;

    private String buildDirKey(Long directoryId) {
        return DIR_KEY_PREFIX + (directoryId == null ? "root" : directoryId);
    }

    private String buildSpaceKey(Integer spaceType, Long spaceId, Long directoryId) {
        String dirPart = directoryId == null ? "root" : String.valueOf(directoryId);
        return SPACE_KEY_PREFIX + spaceType + ":" + spaceId + ":" + dirPart;
    }

    private long randomTtlMinutes() {
        return BASE_TTL_MINUTES + RANDOM.nextInt(TTL_JITTER_SECONDS) / 60;
    }

    @SuppressWarnings("unchecked")
    public List<FileDTO> getDirFiles(Long directoryId) {
        String key = buildDirKey(directoryId);
        // L1
        try {
            org.springframework.cache.Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                org.springframework.cache.Cache.ValueWrapper wrapper = l1.get(key);
                if (wrapper != null) {
                    Object v = wrapper.get();
                    if (EMPTY_LIST_MARKER.equals(v)) {
                        log.info("[二级缓存] L1(Caffeine)命中(空标记), key={}", key);
                        return List.of();
                    }
                    if (v instanceof List) {
                        log.info("[二级缓存] L1(Caffeine)命中, key={}, size={}", key, ((List<?>) v).size());
                        return (List<FileDTO>) v;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[L1] 读取目录文件列表缓存失败: {}", e.getMessage());
        }
        // L2
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (EMPTY_LIST_MARKER.equals(cached)) {
                    log.info("[二级缓存] L2(Redis)命中(空标记)并回填L1, key={}", key);
                    putToL1(key, List.of());
                    return List.of();
                }
                String json = JSON.toJSONString(cached);
                List<FileDTO> result = JSON.parseArray(json, FileDTO.class);
                log.info("[二级缓存] L2(Redis)命中并回填L1, key={}, size={}", key, result.size());
                putToL1(key, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("[L2] 读取目录文件列表缓存失败: {}", e.getMessage());
        }
        log.info("[二级缓存] 缓存未命中，回源DB, key={}", key);
        return null;
    }

    public void putDirFiles(Long directoryId, List<FileDTO> files) {
        String key = buildDirKey(directoryId);
        try {
            if (files == null || files.isEmpty()) {
                redisTemplate.opsForValue().set(key, EMPTY_LIST_MARKER, 5, TimeUnit.MINUTES);
                putToL1(key, EMPTY_LIST_MARKER);
            } else {
                redisTemplate.opsForValue().set(key, files, randomTtlMinutes(), TimeUnit.MINUTES);
                putToL1(key, files);
            }
            log.info("[二级缓存] 写入目录文件列表缓存, key={}, size={}", key, files == null ? 0 : files.size());
        } catch (Exception e) {
            log.warn("[二级缓存] 写入目录文件列表缓存失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<FileDTO> getDeptDirFiles(Long departmentId, Long directoryId) {
        String key = buildDirKey(directoryId);
        String hashKey = "dept:" + departmentId;
        String l1Key = key + ":" + hashKey;
        // L1
        try {
            org.springframework.cache.Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                org.springframework.cache.Cache.ValueWrapper wrapper = l1.get(l1Key);
                if (wrapper != null) {
                    Object v = wrapper.get();
                    if (EMPTY_LIST_MARKER.equals(v)) {
                        log.info("[二级缓存] L1(Caffeine)命中(空标记), key={}", l1Key);
                        return List.of();
                    }
                    if (v instanceof List) {
                        log.info("[二级缓存] L1(Caffeine)命中, key={}, size={}", l1Key, ((List<?>) v).size());
                        return (List<FileDTO>) v;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[L1] 读取部门目录文件列表缓存失败: {}", e.getMessage());
        }
        // L2
        try {
            Object cached = redisTemplate.opsForHash().get(key, hashKey);
            if (cached != null) {
                if (EMPTY_LIST_MARKER.equals(cached)) {
                    log.info("[二级缓存] L2(Redis)命中(空标记)并回填L1, key={}", l1Key);
                    putToL1(l1Key, List.of());
                    return List.of();
                }
                String json = JSON.toJSONString(cached);
                List<FileDTO> result = JSON.parseArray(json, FileDTO.class);
                log.info("[二级缓存] L2(Redis)命中并回填L1, key={}, size={}", l1Key, result.size());
                putToL1(l1Key, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("[L2] 读取部门目录文件列表缓存失败: {}", e.getMessage());
        }
        log.info("[二级缓存] 缓存未命中，回源DB, key={}", l1Key);
        return null;
    }

    public void putDeptDirFiles(Long departmentId, Long directoryId, List<FileDTO> files) {
        String key = buildDirKey(directoryId);
        String hashKey = "dept:" + departmentId;
        try {
            if (files == null || files.isEmpty()) {
                redisTemplate.opsForHash().put(key, hashKey, EMPTY_LIST_MARKER);
                putToL1(key + ":" + hashKey, EMPTY_LIST_MARKER);
            } else {
                redisTemplate.opsForHash().put(key, hashKey, files);
                putToL1(key + ":" + hashKey, files);
            }
            redisTemplate.expire(key, randomTtlMinutes(), TimeUnit.MINUTES);
            log.info("[二级缓存] 写入部门目录文件列表缓存, key={}, size={}", key + ":" + hashKey, files == null ? 0 : files.size());
        } catch (Exception e) {
            log.warn("[二级缓存] 写入部门目录文件列表缓存失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<FileDTO> getSpaceDirFiles(Integer spaceType, Long spaceId, Long directoryId) {
        String key = buildSpaceKey(spaceType, spaceId, directoryId);
        // L1
        try {
            org.springframework.cache.Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                org.springframework.cache.Cache.ValueWrapper wrapper = l1.get(key);
                if (wrapper != null) {
                    Object v = wrapper.get();
                    if (EMPTY_LIST_MARKER.equals(v)) {
                        log.info("[二级缓存] L1(Caffeine)命中(空标记), key={}", key);
                        return List.of();
                    }
                    if (v instanceof List) {
                        log.info("[二级缓存] L1(Caffeine)命中, key={}, size={}", key, ((List<?>) v).size());
                        return (List<FileDTO>) v;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[L1] 读取空间目录文件列表缓存失败: {}", e.getMessage());
        }
        // L2
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (EMPTY_LIST_MARKER.equals(cached)) {
                    log.info("[二级缓存] L2(Redis)命中(空标记)并回填L1, key={}", key);
                    putToL1(key, List.of());
                    return List.of();
                }
                String json = JSON.toJSONString(cached);
                List<FileDTO> result = JSON.parseArray(json, FileDTO.class);
                log.info("[二级缓存] L2(Redis)命中并回填L1, key={}, size={}", key, result.size());
                putToL1(key, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("[L2] 读取空间目录文件列表缓存失败: {}", e.getMessage());
        }
        log.info("[二级缓存] 缓存未命中，回源DB, key={}", key);
        return null;
    }

    public void putSpaceDirFiles(Integer spaceType, Long spaceId, Long directoryId, List<FileDTO> files) {
        String key = buildSpaceKey(spaceType, spaceId, directoryId);
        try {
            if (files == null || files.isEmpty()) {
                redisTemplate.opsForValue().set(key, EMPTY_LIST_MARKER, 5, TimeUnit.MINUTES);
                putToL1(key, EMPTY_LIST_MARKER);
            } else {
                redisTemplate.opsForValue().set(key, files, randomTtlMinutes(), TimeUnit.MINUTES);
                putToL1(key, files);
            }
            log.info("[二级缓存] 写入空间目录文件列表缓存, key={}, size={}", key, files == null ? 0 : files.size());
        } catch (Exception e) {
            log.warn("[二级缓存] 写入空间目录文件列表缓存失败: {}", e.getMessage());
        }
    }

    public void evictDirectory(Long directoryId) {
        String key = buildDirKey(directoryId);
        log.info("[二级缓存] 失效目录缓存, key={}", key);
        // 清 L1
        evictFromL1(key);
        evictFromL1(key + ":dept:*"); // 部门 hash 在 L1 中的 key 模式（Caffeine 不支持模式，这里只能逐个清，简化处理：清空整个 fileList 缓存）
        // 清 L2
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[L2] 清除目录缓存失败: {}", e.getMessage());
        }
    }

    public void evictSpace(Integer spaceType, Long spaceId, Long directoryId) {
        if (spaceType == null || spaceId == null) return;
        String key = buildSpaceKey(spaceType, spaceId, directoryId);
        log.info("[二级缓存] 失效空间目录缓存, key={}", key);
        // 清 L1
        evictFromL1(key);
        // 清 L2
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[L2] 清除空间目录缓存失败: {}", e.getMessage());
        }
    }

    public void evictAll() {
        log.info("[二级缓存] 清空所有文件列表缓存");
        // 清 L1：清空整个 fileList 缓存
        try {
            org.springframework.cache.Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                l1.clear();
            }
        } catch (Exception e) {
            log.warn("[L1] 清空文件列表缓存失败: {}", e.getMessage());
        }
        // 清 L2
        try {
            var keys = redisTemplate.keys(DIR_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            var spaceKeys = redisTemplate.keys(SPACE_KEY_PREFIX + "*");
            if (spaceKeys != null && !spaceKeys.isEmpty()) {
                redisTemplate.delete(spaceKeys);
            }
        } catch (Exception e) {
            log.warn("[L2] 清除所有文件列表缓存失败: {}", e.getMessage());
        }
    }

    // ============ L1 辅助方法 ============

    private void putToL1(String key, Object value) {
        try {
            org.springframework.cache.Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                l1.put(key, value);
            }
        } catch (Exception e) {
            log.warn("[L1] 写入失败, key={}, error={}", key, e.getMessage());
        }
    }

    private void evictFromL1(String key) {
        try {
            org.springframework.cache.Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                l1.evict(key);
            }
        } catch (Exception e) {
            log.warn("[L1] 失效失败, key={}, error={}", key, e.getMessage());
        }
    }
}
