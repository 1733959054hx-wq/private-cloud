package front.mq.handler;

import front.intelligence.ocr.service.OcrService;
import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import front.mq.service.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * OCR 任务处理器
 * 幂等性：Redis 分布式排他锁 + OcrRecord 状态判断双重保障
 */
@Component
public class FileOcrHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(FileOcrHandler.class);

    @Autowired
    private OcrService ocrService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void handle(TaskMessage message) throws Exception {
        Long fileId = message.getBizId();
        log.info("[MQ-OCR] 开始处理, fileId={}, taskId={}", fileId, message.getTaskId());

        // Redis 分布式排他锁：防止并发重复 OCR
        String lockKey = "lock:ocr:" + fileId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[MQ-OCR] 检测到重复 OCR 任务，跳过执行, fileId={}", fileId);
            return;
        }

        try {
            ocrService.triggerOcrAsync(fileId);
            log.info("[MQ-OCR] 处理完成, fileId={}", fileId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public String getTaskType() {
        return MqConstants.TASK_FILE_OCR;
    }
}
