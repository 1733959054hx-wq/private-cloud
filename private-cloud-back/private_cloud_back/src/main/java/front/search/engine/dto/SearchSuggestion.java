package front.search.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索建议DTO - 关键词自动补全
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestion {

    /** 建议关键词 */
    private String keyword;

    /** 搜索次数 */
    private Integer count;

    /** 是否为热门词 */
    private Boolean hot;

    /** 来源：history-用户搜索历史 / document-文档标题匹配 */
    private String source;
}
