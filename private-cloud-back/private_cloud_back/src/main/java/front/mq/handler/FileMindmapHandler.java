package front.mq.handler;

import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import front.mq.service.TaskHandler;
import front.search.qa.service.AiQaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 脑图生成任务处理器
 * 幂等性：Redis 分布式排他锁 + 数据库 mindmap_content 状态双重保障
 */
@Component
public class FileMindmapHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(FileMindmapHandler.class);

    @Autowired
    private AiQaService aiQaService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void handle(TaskMessage message) throws Exception {
        Long fileId = message.getBizId();
        String taskId = message.getTaskId();
        log.info("[MQ-Mindmap] 开始处理, fileId={}, taskId={}", fileId, taskId);

        // Redis 分布式排他锁：防止并发重复生成
        String lockKey = "lock:mindmap:" + fileId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[MQ-Mindmap] 检测到重复脑图生成任务，跳过执行, fileId={}", fileId);
            return;
        }

        try {
            String model = message.getPayload() != null
                    ? (String) message.getPayload().get("model")
                    : null;
            boolean force = message.getPayload() != null
                    && Boolean.TRUE.equals(message.getPayload().get("force"));

            // force=false 时，若数据库已存在缓存则跳过（二次幂等）
            if (!force) {
                String saved = aiQaService.getSavedMindmap(fileId);
                if (saved != null && !saved.isEmpty()) {
                    log.info("[MQ-Mindmap] 数据库已存在脑图缓存，跳过生成, fileId={}", fileId);
                    return;
                }
            }

            String result = aiQaService.generateMindmap(fileId, model, force);
            if (result != null && (result.startsWith("# 脑图生成失败") || result.startsWith("# AI 服务未配置"))) {
                throw new RuntimeException("脑图生成失败: " + result);
            }
            log.info("[MQ-Mindmap] 处理完成, fileId={}", fileId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public String getTaskType() {
        return MqConstants.TASK_FILE_MINDMAP;
    }
}
