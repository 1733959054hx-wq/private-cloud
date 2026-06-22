package front.search.engine.service;

import front.search.engine.dto.SearchRequest;
import front.search.engine.dto.SearchResult;
import front.search.engine.dto.SearchResultWithTotal;
import front.search.engine.dto.SearchSuggestion;
import front.search.engine.entity.DocumentIndex;
import java.util.List;

/**
 * 搜索引擎服务接口
 */
public interface SearchService {

    /**
     * 全文检索（多条件交叉过滤 + 高亮）
     */
    SearchResultWithTotal fullTextSearch(SearchRequest request);

    /**
     * 关键词自动补全
     */
    List<SearchSuggestion> suggestKeywords(String prefix);

    /**
     * 获取热门搜索词
     */
    List<String> getHotKeywords();

    /**
     * 记录搜索关键词
     */
    void recordKeyword(String keyword, Long userId);

    /**
     * 根据部门ID查询文档列表
     */
    List<DocumentIndex> getDocumentsByDepartment(Long departmentId);
}
