package front.search.qa.controller;

import front.search.qa.dto.AiQaRequest;
import front.search.qa.dto.AiQaResponse;
import front.search.qa.service.AiQaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 知识问答控制器
 */
@RestController
@RequestMapping("/api/front/ai")
@PreAuthorize("isAuthenticated()")
public class AiQaController {

    @Resource
    private AiQaService aiQaService;

    @Resource
    private front.mq.service.TaskPublisher taskPublisher;

    /**
     * AI 知识问答（RAG）
     * POST /api/ai/qa
     */
    @PostMapping("/qa")
    public Map<String, Object> askQuestion(@RequestBody AiQaRequest request) {
        AiQaResponse response = aiQaService.askQuestion(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", response);
        return result;
    }

    /**
     * 流式问答（SSE）
     * POST /api/ai/qa/stream
     */
    @PostMapping("/qa/stream")
    public void askQuestionStream(@RequestBody AiQaRequest request, HttpServletResponse response) {
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        // 优化：禁用 Nginx/反向代理缓冲，确保 SSE 实时推送
        response.setHeader("X-Accel-Buffering", "no");
        aiQaService.askQuestionStream(request, response);
    }

    /**
     * 任务三：长文档一键脑图生成
     * POST /api/ai/mindmap/{fileId}  body: { "model": "glm-4.7-flash", "force": false }
     * 支持多模型：glm-4.7-flash / mimo-v2.5 / mimo-v2.5-pro / deepseek-chat 等
     * 后端根据模型名前缀自动路由到对应厂商的 API。
     * 优先返回数据库缓存；force=true 时忽略缓存重新生成。
     */
    @PostMapping("/mindmap/{fileId}")
    public Map<String, Object> generateMindmap(@PathVariable Long fileId,
                                                @RequestBody(required = false) Map<String, Object> body) {
        String model = body != null ? (String) body.get("model") : null;
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        String mindmap = aiQaService.generateMindmap(fileId, model, force);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", mindmap);
        return result;
    }

    /**
     * 获取已持久化保存的脑图（不触发 AI 生成）
     * GET /api/ai/mindmap/{fileId}/saved
     */
    @GetMapping("/mindmap/{fileId}/saved")
    public Map<String, Object> getSavedMindmap(@PathVariable Long fileId) {
        String mindmap = aiQaService.getSavedMindmap(fileId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", mindmap);
        return result;
    }

    /**
     * 提交脑图生成任务到 RabbitMQ（任务三与 MQ 整合）
     * POST /api/ai/mindmap/{fileId}/submit
     * 返回：{ taskId, fileId, mode }
     * mode = 'mq' 表示已提交到 RabbitMQ 异步处理；mode = 'sync' 表示 MQ 不可用，已同步生成
     */
    @PostMapping("/mindmap/{fileId}/submit")
    public Map<String, Object> submitMindmapTask(@PathVariable Long fileId,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        String model = body != null ? (String) body.get("model") : null;
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));

        // 如果未强制重新生成，先检查缓存
        if (!force) {
            String saved = aiQaService.getSavedMindmap(fileId);
            if (saved != null && !saved.isEmpty()) {
                Map<String, Object> cachedResult = new HashMap<>();
                cachedResult.put("code", 200);
                cachedResult.put("data", Map.of("fileId", fileId, "mode", "cached", "content", saved));
                return cachedResult;
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("force", force);

        String taskId = taskPublisher.publish(front.mq.config.MqConstants.TASK_FILE_MINDMAP, fileId, payload);
        Map<String, Object> data = new HashMap<>();
        data.put("fileId", fileId);

        if (taskId != null) {
            data.put("taskId", taskId);
            data.put("mode", "mq");
        } else {
            // MQ 不可用，降级为同步生成
            String mindmap = aiQaService.generateMindmap(fileId, model, force);
            data.put("mode", "sync");
            data.put("content", mindmap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return result;
    }

    /**
     * 查询脑图生成任务状态
     * GET /api/ai/mindmap/{fileId}/status
     * 返回：{ status: 'pending'|'completed'|'failed', content?: string, failReason?: string }
     */
    @GetMapping("/mindmap/{fileId}/status")
    public Map<String, Object> getMindmapTaskStatus(@PathVariable Long fileId) {
        String saved = aiQaService.getSavedMindmap(fileId);
        Map<String, Object> data = new HashMap<>();
        data.put("fileId", fileId);

        if (saved != null && !saved.isEmpty()) {
            if (saved.startsWith("# 脑图生成失败") || saved.startsWith("# AI 服务未配置")) {
                data.put("status", "failed");
                data.put("failReason", saved);
            } else {
                data.put("status", "completed");
                data.put("content", saved);
            }
        } else {
            data.put("status", "pending");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", data);
        return result;
    }
}
