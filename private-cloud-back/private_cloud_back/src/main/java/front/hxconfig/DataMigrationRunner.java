package front.hxconfig;

import front.system.service.SysUserService;
import front.workspace.documentspace.entity.DocDirectory;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocDirectoryRepository;
import front.workspace.documentspace.repository.DocFileRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataMigrationRunner {

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private DocDirectoryRepository directoryRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SysUserService sysUserService;

    @PostConstruct
    public void migrateSpaceFields() {
        try {
            migrateFiles();
            migrateDirectories();
        } catch (Exception e) {
            System.err.println("[数据迁移] 空间字段回填失败: " + e.getMessage());
        }
        try {
            sysUserService.migrateAllLegacyPasswords();
        } catch (Exception e) {
            System.err.println("[数据迁移] 用户密码迁移失败: " + e.getMessage());
        }
        try {
            clearStaleRedisCache();
        } catch (Exception e) {
            System.err.println("[数据迁移] 清理旧Redis缓存失败: " + e.getMessage());
        }
    }

    private void migrateFiles() {
        int count = 0;
        Iterable<DocFile> files = fileRepository.findAll();
        for (DocFile file : files) {
            boolean changed = false;
            if (file.getSpaceType() == null) {
                file.setSpaceType(0);
                changed = true;
            }
            if (file.getSpaceId() == null && file.getUploaderId() != null) {
                file.setSpaceId(file.getUploaderId());
                changed = true;
            }
            if (changed) {
                fileRepository.save(file);
                count++;
            }
        }
        if (count > 0) {
            System.out.println("[数据迁移] 回填文件空间字段完成，影响行数: " + count);
        }
    }

    private void migrateDirectories() {
        int count = 0;
        Iterable<DocDirectory> dirs = directoryRepository.findAll();
        for (DocDirectory dir : dirs) {
            boolean changed = false;
            if (dir.getSpaceType() == null) {
                dir.setSpaceType(0);
                changed = true;
            }
            if (dir.getSpaceId() == null && dir.getOwnerId() != null) {
                dir.setSpaceId(dir.getOwnerId());
                changed = true;
            }
            if (changed) {
                directoryRepository.save(dir);
                count++;
            }
        }
        if (count > 0) {
            System.out.println("[数据迁移] 回填目录空间字段完成，影响行数: " + count);
        }
    }

    private void clearStaleRedisCache() {
        String[] prefixes = {
                "sys:user:info:", "sys:user:name:", "sys:user:roles:", "sys:user:perms:",
                "file:list:dir:", "file:list:space:",
                "dashboard:data:"
        };
        int totalCleared = 0;
        for (String prefix : prefixes) {
            try {
                var keys = redisTemplate.keys(prefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    totalCleared += keys.size();
                }
            } catch (Exception e) {
                System.err.println("[数据迁移] 清理缓存前缀 " + prefix + " 失败: " + e.getMessage());
            }
        }
        if (totalCleared > 0) {
            System.out.println("[数据迁移] 清理旧格式Redis缓存完成，清除键数: " + totalCleared);
        }
    }
}
