package front.search.qa.dto;

import lombok.Data;
import java.util.List;

/**
 * AI 知识问答响应
 */
@Data
public class AiQaResponse {
    /** AI 回答内容 */
    private String answer;
    /** 引用来源文档列表 */
    private List<Reference> references;
    /** 当前对话使用的 AI 模式标签（如：精准提取模式） */
    private String modeLabel;

    @Data
    public static class Reference {
        /** 文档ID */
        private Long documentId;
        /** 文档标题 */
        private String title;
        /** 匹配片段 */
        private String snippet;
        /** 相关度得分 */
        private Float score;
    }
}
