package front.search.qa.service;

import front.search.qa.dto.AiQaRequest;
import front.search.qa.dto.AiQaResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * AI 知识问答服务接口
 */
public interface AiQaService {

    /**
     * RAG 知识问答：检索相关文档 → 构建上下文 → 调用 AI → 返回回答+来源
     */
    AiQaResponse askQuestion(AiQaRequest request);

    /**
     * 流式问答（SSE），通过 HttpServletResponse 逐块写入
     */
    void askQuestionStream(AiQaRequest request, HttpServletResponse response);

    /**
     * 任务三：长文档一键脑图生成
     * 基于文件全文内容（含页码锚点），调用大模型生成 Markdown 格式的脑图，
     * 节点中附带页码数据属性（data-page），前端可据此实现"点击节点跳转到对应页"。
     * 优先读取数据库缓存；若 force=false 且存在缓存，直接返回缓存内容。
     *
     * @param fileId 文件ID
     * @param model  AI 模型名（可为空，空则使用默认配置模型）
     * @param force  是否强制重新生成（忽略缓存）
     * @return Markdown 格式的脑图内容（兼容 markmap 渲染）
     */
    String generateMindmap(Long fileId, String model, boolean force);

    /**
     * 获取已持久化保存的脑图（不触发 AI 生成，从数据库读取）
     * @param fileId 文件ID
     * @return 脑图内容，未保存返回 null
     */
    String getSavedMindmap(Long fileId);
}
