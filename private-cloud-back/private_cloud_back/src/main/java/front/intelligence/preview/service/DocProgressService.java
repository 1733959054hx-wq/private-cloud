package front.intelligence.preview.service;

import front.intelligence.preview.dto.ProgressDTO;
import front.intelligence.preview.entity.DocProgressRecord;
import front.intelligence.preview.repository.DocProgressRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class DocProgressService {

    private static final String PROGRESS_PREFIX = "doc:progress:";
    private static final long PROGRESS_TTL_DAYS = 30;

    @Autowired
    private DocProgressRecordRepository progressRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 保存阅读进度：先写 Redis（毫秒级），再异步写 DB（持久化兜底）
     */
    @Transactional
    public void saveOrUpdateProgress(Long userId, ProgressDTO dto) {
        // 1. 写入 Redis（Key: doc:progress:{userId}:{fileId}:{progressType}）
        String redisKey = PROGRESS_PREFIX + userId + ":" + dto.getFileId() + ":" + dto.getProgressType();
        stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(dto.getProgressValue()),
                PROGRESS_TTL_DAYS, TimeUnit.DAYS);

        // 2. 异步写入数据库（持久化兜底，防止 Redis 数据丢失）
        asyncSaveToDb(userId, dto);
    }

    @Async("taskExecutor")
    protected void asyncSaveToDb(Long userId, ProgressDTO dto) {
        try {
            DocProgressRecord record = progressRepository
                    .findByUserIdAndFileIdAndProgressType(userId, dto.getFileId(), dto.getProgressType())
                    .orElse(new DocProgressRecord());

            record.setUserId(userId);
            record.setFileId(dto.getFileId());
            record.setProgressType(dto.getProgressType());
            record.setProgressValue(dto.getProgressValue());

            progressRepository.save(record);
        } catch (Exception e) {
            // DB 写入失败不影响 Redis 缓存已保存的进度
            System.err.println("[阅读进度] DB持久化失败, userId=" + userId + ", fileId=" + dto.getFileId() + ", error=" + e.getMessage());
        }
    }

    /**
     * 获取阅读进度：优先从 Redis 读取（毫秒级），未命中则查 DB 并回填 Redis
     */
    public Double getProgress(Long userId, Long fileId, Integer progressType) {
        // 1. 优先查 Redis
        String redisKey = PROGRESS_PREFIX + userId + ":" + fileId + ":" + progressType;
        String cached = stringRedisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            try {
                return Double.parseDouble(cached);
            } catch (NumberFormatException ignored) { /* 降级查 DB */ }
        }

        // 2. Redis 未命中，查数据库
        Double value = progressRepository
                .findByUserIdAndFileIdAndProgressType(userId, fileId, progressType)
                .map(DocProgressRecord::getProgressValue)
                .orElse(0D);

        // 3. 回填 Redis 缓存
        if (value > 0) {
            stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(value),
                    PROGRESS_TTL_DAYS, TimeUnit.DAYS);
        }

        return value;
    }
}
