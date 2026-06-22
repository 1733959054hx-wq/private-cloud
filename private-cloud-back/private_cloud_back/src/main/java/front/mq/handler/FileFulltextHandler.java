package front.mq.handler;

import front.intelligence.text.service.FulltextExtractService;
import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import front.mq.service.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 全文提取任务处理器
 */
@Component
public class FileFulltextHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(FileFulltextHandler.class);

    @Autowired
    private FulltextExtractService fulltextExtractService;

    @Override
    public void handle(TaskMessage message) throws Exception {
        Long fileId = message.getBizId();
        log.info("[MQ-Fulltext] 开始处理, fileId={}, taskId={}", fileId, message.getTaskId());
        fulltextExtractService.extractAndSave(fileId);
        log.info("[MQ-Fulltext] 处理完成, fileId={}", fileId);
    }

    @Override
    public String getTaskType() {
        return MqConstants.TASK_FILE_FULLTEXT;
    }
}
