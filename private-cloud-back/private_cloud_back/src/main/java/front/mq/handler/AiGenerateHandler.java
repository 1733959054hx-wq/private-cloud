package front.mq.handler;

import front.intelligence.ai.entity.GeneratedDoc;
import front.intelligence.ai.service.DocumentGenerateService;
import front.intelligence.ai.service.GeneratedDocService;
import front.mq.config.MqConstants;
import front.mq.dto.TaskMessage;
import front.mq.service.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 文档生成任务处理器
 * 幂等：先查 generated_doc 状态，若已成功（status=1）则直接跳过
 */
@Component
public class AiGenerateHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AiGenerateHandler.class);

    @Autowired
    private DocumentGenerateService documentGenerateService;

    @Autowired
    private GeneratedDocService generatedDocService;

    @Override
    public void handle(TaskMessage message) throws Exception {
        Long docId = message.getBizId();
        log.info("[MQ-AI] 开始处理, docId={}, taskId={}", docId, message.getTaskId());

        // 幂等检查：若文档状态已为成功（1）或失败（2），直接跳过
        GeneratedDoc doc = generatedDocService.getDocById(docId);
        if (doc == null) {
            log.warn("[MQ-AI] 文档不存在, docId={}", docId);
            return;
        }
        if (doc.getStatus() != null && doc.getStatus() == 1) {
            log.info("[MQ-AI] 文档已生成成功，跳过, docId={}", docId);
            return;
        }

        Map<String, Object> payload = message.getPayload();
        String templateId = (String) payload.get("templateId");
        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) payload.get("params");
        String model = (String) payload.get("model");
        Long userId = payload.get("userId") != null ? Long.valueOf(payload.get("userId").toString()) : null;
        String referenceContent = (String) payload.get("referenceContent");

        // 执行 AI 生成
        Map<String, Object> result = documentGenerateService.executeAiGeneration(
                templateId, params, model, userId, referenceContent);

        String content = (String) result.get("content");
        String filePath = (String) result.get("filePath");

        // 更新文档状态为成功
        generatedDocService.updateDocContentAndStatus(docId, 1, content, filePath);
        log.info("[MQ-AI] 处理完成, docId={}", docId);
    }

    @Override
    public String getTaskType() {
        return MqConstants.TASK_AI_GENERATE;
    }
}
