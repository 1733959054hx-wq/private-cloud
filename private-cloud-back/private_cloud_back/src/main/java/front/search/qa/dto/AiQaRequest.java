package front.search.qa.dto;

import lombok.Data;
import java.util.List;

/**
 * AI 知识问答请求
 */
@Data
public class AiQaRequest {
    /** 用户问题 */
    private String question;
    /** 对话历史（可选） */
    private List<Message> messages;
    /** 限定搜索部门（为空则全部门搜索） */
    private Long departmentId;
    /** 用户选择的系统提示词（角色设定/输出格式/知识边界） */
    private String systemPrompt;
    /** AI 模式标签（如：精准提取模式、总结概括模式、代码解析模式） */
    private String modeLabel;
    /** 用户选择的 AI 模型（如 qwen-plus、deepseek-chat 等） */
    private String model;
    /** 是否启用 RAG 检索 */
    private Boolean useRag;
}
