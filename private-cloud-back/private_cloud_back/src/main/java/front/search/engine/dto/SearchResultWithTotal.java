package front.search.engine.dto;

import java.util.List;

/** 搜索结果 + 总命中数 */
public class SearchResultWithTotal {
    private List<SearchResult> list;
    private long total;

    public SearchResultWithTotal() {}
    public SearchResultWithTotal(List<SearchResult> list, long total) {
        this.list = list; this.total = total;
    }

    public List<SearchResult> getList() { return list; }
    public void setList(List<SearchResult> list) { this.list = list; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
