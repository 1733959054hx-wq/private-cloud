package front.workspace.documentspace.service;

import front.workspace.documentspace.dto.DirectoryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 目录树缓存服务
 *
 * 缓存结构：
 *   Key:   dir:tree:{deptId}      （部门目录树，deptId=0 表示公共/所有部门）
 *          dir:tree:space:{type}:{spaceId}  （空间目录树）
 *   Type:  String (JSON)
 *   TTL:   30~60min (基础 45min + 随机抖动)
 *
 * 设计动机：
 *   1. 每次进入文件空间都会加载目录树
 *   2. 目录的增删改操作相对低频，适合较长 TTL
 *   3. 数据变更时主动失效缓存，保证一致性
 */
@Service
public class DirectoryTreeCacheService {

    private static final String DEPT_TREE_PREFIX = "dir:tree:";
    private static final String SPACE_TREE_PREFIX = "dir:tree:space:";
    private static final long BASE_TTL_MINUTES = 45;
    private static final long JITTER_MINUTES = 15;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 读取部门目录树缓存
     */
    @SuppressWarnings("unchecked")
    public List<DirectoryDTO> getDeptTree(Long departmentId) {
        try {
            String key = DEPT_TREE_PREFIX + (departmentId == null ? 0 : departmentId);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            String json = com.alibaba.fastjson.JSON.toJSONString(cached);
            return com.alibaba.fastjson.JSON.parseArray(json, DirectoryDTO.class);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取部门目录树失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 写入部门目录树缓存
     */
    public void putDeptTree(Long departmentId, List<DirectoryDTO> tree) {
        try {
            String key = DEPT_TREE_PREFIX + (departmentId == null ? 0 : departmentId);
            redisTemplate.opsForValue().set(key, tree, randomTtlMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 写入部门目录树失败: " + e.getMessage());
        }
    }

    /**
     * 读取空间目录树缓存
     */
    @SuppressWarnings("unchecked")
    public List<DirectoryDTO> getSpaceTree(Integer spaceType, Long spaceId) {
        try {
            String key = SPACE_TREE_PREFIX + spaceType + ":" + spaceId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            String json = com.alibaba.fastjson.JSON.toJSONString(cached);
            return com.alibaba.fastjson.JSON.parseArray(json, DirectoryDTO.class);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取空间目录树失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 写入空间目录树缓存
     */
    public void putSpaceTree(Integer spaceType, Long spaceId, List<DirectoryDTO> tree) {
        try {
            String key = SPACE_TREE_PREFIX + spaceType + ":" + spaceId;
            redisTemplate.opsForValue().set(key, tree, randomTtlMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 写入空间目录树失败: " + e.getMessage());
        }
    }

    /**
     * 清除指定部门的目录树缓存
     */
    public void evictDeptTree(Long departmentId) {
        try {
            String key = DEPT_TREE_PREFIX + (departmentId == null ? 0 : departmentId);
            redisTemplate.delete(key);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 清除部门目录树失败: " + e.getMessage());
        }
    }

    /**
     * 清除指定空间的目录树缓存
     */
    public void evictSpaceTree(Integer spaceType, Long spaceId) {
        try {
            String key = SPACE_TREE_PREFIX + spaceType + ":" + spaceId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 清除空间目录树失败: " + e.getMessage());
        }
    }

    /**
     * 目录增删改时调用，根据目录所属部门/空间同时失效对应缓存
     */
    public void evictAll(Long departmentId, Integer spaceType, Long spaceId) {
        evictDeptTree(departmentId);
        if (spaceType != null && spaceId != null) {
            evictSpaceTree(spaceType, spaceId);
        }
    }

    private long randomTtlMinutes() {
        return BASE_TTL_MINUTES + (long) (Math.random() * JITTER_MINUTES);
    }
}
