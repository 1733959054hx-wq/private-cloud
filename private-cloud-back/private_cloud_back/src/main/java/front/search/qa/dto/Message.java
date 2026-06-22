package front.search.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    /** 角色：system / user / assistant */
    private String role;
    /** 消息内容 */
    private String content;
}
