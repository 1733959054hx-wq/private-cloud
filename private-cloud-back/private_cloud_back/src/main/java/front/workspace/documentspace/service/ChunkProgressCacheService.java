package front.workspace.documentspace.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 分片上传进度缓存服务
 *
 * 缓存结构：
 *   Key:   upload:chunk:{fileId}
 *   Type:  Hash
 *   Field: meta              -> {"totalChunks":N,"uploaderId":M,"fileName":"..."}
 *          chunk:{index}     -> "1" (1=已接收)
 *   TTL:   24h
 *
 * 设计动机：
 *   1. 断点续传需要频繁查询「已接收分片索引集合」与「总片数」
 *   2. 原方案每次都查 doc_upload_task 表，Redis Hash 比数据库快数个量级
 *   3. 上传完成（合并）后主动删除缓存，避免长期占用
 */
@Service
public class ChunkProgressCacheService {

    private static final String CHUNK_KEY_PREFIX = "upload:chunk:";
    private static final String META_FIELD = "meta";
    private static final String CHUNK_FIELD_PREFIX = "chunk:";
    private static final long CHUNK_CACHE_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 初始化上传任务的缓存（首次创建上传任务时调用）
     */
    public void initProgress(String fileId, int totalChunks, Long uploaderId, String fileName) {
        try {
            String key = CHUNK_KEY_PREFIX + fileId;
            Map<String, Object> meta = new HashMap<>();
            meta.put("totalChunks", totalChunks);
            meta.put("uploaderId", uploaderId);
            meta.put("fileName", fileName);
            meta.put("receivedChunks", 0);

            redisTemplate.opsForHash().put(key, META_FIELD, meta);
            redisTemplate.expire(key, CHUNK_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 初始化分片进度缓存失败: " + e.getMessage());
        }
    }

    /**
     * 标记某个分片已接收
     */
    public void markChunkReceived(String fileId, int chunkIndex) {
        try {
            String key = CHUNK_KEY_PREFIX + fileId;
            redisTemplate.opsForHash().put(key, CHUNK_FIELD_PREFIX + chunkIndex, "1");
            redisTemplate.expire(key, CHUNK_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 标记分片接收失败: " + e.getMessage());
        }
    }

    /**
     * 获取已接收的分片索引集合
     *
     * @return 已接收的分片索引集合（可能为 null 表示无缓存）
     */
    @SuppressWarnings("unchecked")
    public Set<Integer> getReceivedChunkIndexes(String fileId) {
        try {
            String key = CHUNK_KEY_PREFIX + fileId;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            Set<Integer> indexes = new java.util.HashSet<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String field = String.valueOf(entry.getKey());
                if (field.startsWith(CHUNK_FIELD_PREFIX)) {
                    try {
                        indexes.add(Integer.parseInt(field.substring(CHUNK_FIELD_PREFIX.length())));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return indexes;
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取已接收分片索引失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取任务元信息
     *
     * @return Map: totalChunks, uploaderId, fileName, receivedChunks；若不存在返回 null
     */
    public Map<String, Object> getMeta(String fileId) {
        try {
            String key = CHUNK_KEY_PREFIX + fileId;
            Object meta = redisTemplate.opsForHash().get(key, META_FIELD);
            if (meta == null) {
                return null;
            }
            return com.alibaba.fastjson.JSON.parseObject(
                    com.alibaba.fastjson.JSON.toJSONString(meta), Map.class);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取分片任务元信息失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 查询断点续传时所需的全部信息（总片数 + 已接收分片索引）
     */
    public Map<String, Object> getResumeInfo(String fileId) {
        Map<String, Object> meta = getMeta(fileId);
        if (meta == null) {
            return null;
        }
        Set<Integer> received = getReceivedChunkIndexes(fileId);
        if (received == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", meta.get("totalChunks"));
        result.put("uploaderId", meta.get("uploaderId"));
        result.put("fileName", meta.get("fileName"));
        result.put("receivedChunks", received.size());
        result.put("receivedIndexes", new java.util.ArrayList<>(received));
        return result;
    }

    /**
     * 上传完成（合并/取消）时清理缓存
     */
    public void evictProgress(String fileId) {
        try {
            String key = CHUNK_KEY_PREFIX + fileId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 清除分片进度缓存失败: " + e.getMessage());
        }
    }

    /**
     * 重建缓存：把数据库中的 receivedChunks 同步到缓存（首次查询时回填）
     */
    public void warmupProgress(String fileId, int totalChunks, long uploaderId, String fileName, List<Integer> receivedIndexes) {
        try {
            initProgress(fileId, totalChunks, uploaderId, fileName);
            if (receivedIndexes != null && !receivedIndexes.isEmpty()) {
                String key = CHUNK_KEY_PREFIX + fileId;
                Map<String, String> chunkMap = new HashMap<>();
                for (Integer idx : receivedIndexes) {
                    chunkMap.put(CHUNK_FIELD_PREFIX + idx, "1");
                }
                redisTemplate.opsForHash().putAll(key, chunkMap);
                redisTemplate.expire(key, CHUNK_CACHE_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            System.err.println("[Redis缓存] 回填分片进度缓存失败: " + e.getMessage());
        }
    }
}
