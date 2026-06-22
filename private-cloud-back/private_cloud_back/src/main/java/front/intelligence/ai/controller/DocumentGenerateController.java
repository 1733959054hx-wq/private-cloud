package front.intelligence.ai.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.intelligence.ai.dto.GenerateDocumentDTO;
import front.intelligence.ai.service.DocumentGenerateService;
import front.intelligence.ai.service.GeneratedDocService;
import front.mq.config.MqConstants;
import front.mq.service.TaskPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/front/ai/generate")
public class DocumentGenerateController {

    @Autowired
    private DocumentGenerateService documentGenerateService;

    @Autowired
    private GeneratedDocService generatedDocService;

    @Autowired
    private TaskPublisher taskPublisher;

    // 有界线程池：MQ 不可用时的降级执行，或同步生成模式
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 16, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "doc-generate-" + counter.incrementAndGet());
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    private final ConcurrentHashMap<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GenerateTaskInfo> taskInfoMap = new ConcurrentHashMap<>();

    @PostMapping
    public Result<Map<String, Object>> generateDocument(
            @RequestBody GenerateDocumentDTO request,
            Authentication authentication) {
        try {
            Long userId = null;
            if (authentication != null) {
                userId = (Long) authentication.getPrincipal();
            }
            Map<String, Object> result = documentGenerateService.generateDocument(
                    request.getTemplateId(), request.getParams(), request.getModel(), userId, request.getReferenceContent());
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("文档生成失败: " + e.getMessage());
        }
    }

    /**
     * 异步提交文档生成任务，瞬间返回 taskId
     * 优先走 MQ，MQ 不可用时降级为线程池 + SSE
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submitGenerateTask(
            @RequestBody GenerateDocumentDTO request,
            Authentication authentication) {
        try {
            Long userId = null;
            if (authentication != null) {
                userId = (Long) authentication.getPrincipal();
            }

            // 创建状态为"生成中"的文档记录
            Long docId = documentGenerateService.createPendingDoc(request.getTemplateId(), request.getModel(), userId);

            // 构造 MQ 消息 payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("templateId", request.getTemplateId());
            payload.put("params", request.getParams());
            payload.put("model", request.getModel());
            payload.put("userId", userId);
            payload.put("referenceContent", request.getReferenceContent());

            // 优先走 MQ
            String taskId = taskPublisher.publish(MqConstants.TASK_AI_GENERATE, docId, payload);

            if (taskId != null) {
                // MQ 发送成功，返回 taskId + docId，前端通过轮询接口查询状态
                Map<String, Object> result = new HashMap<>();
                result.put("taskId", taskId);
                result.put("docId", docId);
                result.put("mode", "mq");
                return Result.success(result);
            }

            // MQ 不可用，降级为线程池 + SSE 模式
            String sseTaskId = UUID.randomUUID().toString();
            SseEmitter emitter = new SseEmitter(300000L);
            emitterMap.put(sseTaskId, emitter);

            GenerateTaskInfo taskInfo = new GenerateTaskInfo(
                    request.getTemplateId(), request.getParams(), request.getModel(),
                    userId, request.getReferenceContent(), docId);
            taskInfoMap.put(sseTaskId, taskInfo);

            Long finalUserId = userId;
            executor.execute(() -> {
                try {
                    SseEmitter e = emitterMap.get(sseTaskId);
                    if (e == null) return;

                    e.send(SseEmitter.event().name("EXTRACTING").data("正在分析参数与参考文档..."));
                    Thread.sleep(500);

                    e.send(SseEmitter.event().name("GENERATING").data("AI 正在深度思考并撰写文档..."));

                    Map<String, Object> result = documentGenerateService.executeAiGeneration(
                            taskInfo.templateId(), taskInfo.params(), taskInfo.model(),
                            finalUserId, taskInfo.referenceContent());

                    String content = (String) result.get("content");
                    String filePath = (String) result.get("filePath");

                    generatedDocService.updateDocContentAndStatus(taskInfo.docId(), 1, content, filePath);

                    e.send(SseEmitter.event().name("COMPLETED").data(result));
                    e.complete();
                } catch (Exception ex) {
                    try {
                        String reason = ex.getMessage() != null ? ex.getMessage() : "未知错误";
                        generatedDocService.updateDocStatus(taskInfo.docId(), 2, reason);
                        SseEmitter e = emitterMap.get(sseTaskId);
                        if (e != null) {
                            e.send(SseEmitter.event().name("FAILED").data("文档生成失败: " + reason));
                            e.complete();
                        }
                    } catch (Exception ignored) {}
                } finally {
                    emitterMap.remove(sseTaskId);
                    taskInfoMap.remove(sseTaskId);
                }
            });

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", sseTaskId);
            result.put("docId", docId);
            result.put("mode", "sse");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("任务提交失败: " + e.getMessage());
        }
    }

    /**
     * SSE 流式监听任务进度（仅降级模式使用）
     */
    @GetMapping(value = "/stream/{taskId}", produces = "text/event-stream")
    public SseEmitter streamTaskProgress(@PathVariable String taskId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        GenerateTaskInfo taskInfo = taskInfoMap.get(taskId);
        // 任务存在但用户不匹配，返回无权限
        if (taskInfo != null && !taskInfo.userId().equals(userId)) {
            SseEmitter err = new SseEmitter();
            try {
                err.send(SseEmitter.event().name("FAILED").data("无权限访问该任务"));
                err.complete();
            } catch (Exception ignored) {}
            return err;
        }
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter != null) {
            return emitter;
        }
        SseEmitter errorEmitter = new SseEmitter();
        try {
            errorEmitter.send(SseEmitter.event().name("FAILED").data("任务不存在或已过期"));
            errorEmitter.complete();
        } catch (Exception ignored) {}
        return errorEmitter;
    }

    /**
     * 轮询查询任务状态（MQ 模式推荐使用）
     * 前端通过该接口轮询 docId 对应的生成状态
     */
    @GetMapping("/status/{docId}")
    public Result<Map<String, Object>> getTaskStatus(@PathVariable Long docId, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        var doc = generatedDocService.getDocById(docId);
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }
        // 权限校验：仅创建者可查询生成状态
        if (doc.getCreatorId() != null && !userId.equals(doc.getCreatorId())) {
            return Result.error(403, "无权访问该文档");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("docId", docId);
        result.put("status", doc.getStatus()); // 0=生成中, 1=成功, 2=失败
        result.put("title", doc.getTitle());
        result.put("fileName", doc.getFileName());
        result.put("failReason", doc.getFailReason());
        if (doc.getStatus() != null && doc.getStatus() == 1) {
            result.put("content", doc.getContent());
            result.put("filePath", doc.getFilePath());
        }
        return Result.success(result);
    }

    @PostMapping("/extract-fields")
    public Result<Map<String, String>> extractFields(
            @RequestBody Map<String, Object> request) {
        try {
            String templateId = (String) request.get("templateId");
            String referenceContent = (String) request.get("referenceContent");
            String model = (String) request.get("model");
            
            Map<String, String> result = documentGenerateService.extractFields(templateId, referenceContent, model);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("AI 智能提取填充失败: " + e.getMessage());
        }
    }

    /**
     * 异步任务信息内部记录
     */
    private record GenerateTaskInfo(
            String templateId, Map<String, String> params, String model,
            Long userId, String referenceContent, Long docId
    ) {
        GenerateTaskInfo(String templateId, Map<String, String> params, String model,
                         Long userId, String referenceContent) {
            this(templateId, params, model, userId, referenceContent, null);
        }
    }
}
