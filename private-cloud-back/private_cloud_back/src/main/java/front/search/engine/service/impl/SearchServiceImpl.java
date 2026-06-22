package front.search.engine.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import front.search.engine.dto.SearchRequest;
import front.search.engine.dto.SearchResult;
import front.search.engine.dto.SearchResultWithTotal;
import front.search.engine.dto.SearchSuggestion;
import front.search.engine.entity.DocumentIndex;
import front.intelligence.search.entity.SearchKeyword;
import front.search.engine.es.DocFileDocument;
import front.search.engine.es.DocFileSearchRepository;
import front.search.engine.mapper.SearchKeywordMapper;
import front.search.engine.service.SearchService;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import front.intelligence.ai.repository.DocTagRepository;
import front.intelligence.ai.entity.DocTag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索引擎服务实现
 *
 * 双引擎架构：
 *   1. Elasticsearch IK 分词检索（主）— BM25 排序 + 高亮
 *   2. MySQL doc_file 表全文索引（降级）— 当 ES 不可用时自动回退
 */
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired(required = false)
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired(required = false)
    private co.elastic.clients.elasticsearch.ElasticsearchClient esClient;

    @Resource
    private DocFileRepository docFileRepository;

    @Autowired(required = false)
    private DocFileSearchRepository docFileSearchRepository;

    @Resource
    private SearchKeywordMapper searchKeywordMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DocTagRepository docTagRepository;

    private static final String HOT_KEYWORDS_ZSET_KEY = "search:hot:keywords";

    // ==================== 全文检索 ====================

    @Override
    @Transactional
    public SearchResultWithTotal fullTextSearch(SearchRequest request) {
        if (StrUtil.isNotBlank(request.getKeyword())) {
            recordKeyword(request.getKeyword(), null);
        }

        if (StrUtil.isNotBlank(request.getKeyword())) {
            try {
                SearchResultWithTotal esResults = searchByEs(request);
                if (esResults.getList().isEmpty()) {
                    System.out.println("[搜索] ES 返回空结果，降级至 MySQL LIKE 检索");
                    return searchByMysqlFulltext(request);
                }
                return esResults;
            } catch (Exception e) {
                System.err.println("[搜索] ES 不可用，降级至 MySQL 全文索引: " + e.getMessage());
                return searchByMysqlFulltext(request);
            }
        }

        return searchByMysqlFilter(request);
    }

    // ==================== ES 检索 ====================

    // ==================== 权限过滤辅助方法 ====================

    /**
     * 构建 ES 权限过滤 query（管理员返回 null 表示不过滤）
     */
    private Query buildPermissionFilter(SearchRequest request) {
        if (Boolean.TRUE.equals(request.getCurrentIsAdmin())) return null;

        Long userId = request.getCurrentUserId();
        Long deptId = request.getCurrentDepartmentId();
        if (userId == null) return null; // 无法获取用户身份时不加过滤

        var permBuilder = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();

        // 条件A：个人空间自己的文档
        permBuilder.should(Query.of(q -> q
                .bool(b -> b.must(List.of(
                        Query.of(q2 -> q2.term(t -> t.field("spaceType").value(0))),
                        Query.of(q2 -> q2.term(t -> t.field("spaceId").value(userId)))
                )))
        ));

        if (deptId != null) {
            // 条件B：部门空间 + 企业空间（同部门）
            permBuilder.should(Query.of(q -> q
                    .bool(b -> b.must(List.of(
                            Query.of(q2 -> q2.terms(t -> t.field("spaceType").terms(t2 -> t2.value(List.of(
                                    co.elastic.clients.elasticsearch._types.FieldValue.of(fv -> fv.longValue(1)),
                                    co.elastic.clients.elasticsearch._types.FieldValue.of(fv -> fv.longValue(2))
                            ))))),
                            Query.of(q2 -> q2.term(t -> t.field("departmentId").value(deptId)))
                    )))
            ));
        }

        // 条件C：自己上传的文档（跨空间也能看到）
        permBuilder.should(Query.of(q -> q
                .term(t -> t.field("uploaderId").value(userId))
        ));

        permBuilder.minimumShouldMatch("1");
        return Query.of(q -> q.bool(permBuilder.build()));
    }

    /** 判断用户是否有权访问某个文件（MySQL 降级路径用） */
    private boolean canAccessFile(DocFile f, SearchRequest request) {
        if (Boolean.TRUE.equals(request.getCurrentIsAdmin())) return true;
        Long userId = request.getCurrentUserId();
        if (userId == null) return true;
        // 自己上传的
        if (userId.equals(f.getUploaderId())) return true;
        // 个人空间，只能看自己的
        if (f.getSpaceType() != null && f.getSpaceType() == 0 && userId.equals(f.getSpaceId())) return true;
        // 部门空间或企业空间，同部门的可看
        if (f.getSpaceType() != null && (f.getSpaceType() == 1 || f.getSpaceType() == 2)) {
            Long deptId = request.getCurrentDepartmentId();
            if (deptId != null && deptId.equals(f.getDepartmentId())) return true;
        }
        return false;
    }

    // ==================== ES 检索 ====================

    private SearchResultWithTotal searchByEs(SearchRequest request) throws java.io.IOException {
        var boolBuilder = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();

        boolBuilder.must(Query.of(q -> q
                .multiMatch(m -> m
                        .fields("fileName^3", "fulltextContent^0.5")
                        .query(request.getKeyword())
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                )
        ));

        if (StrUtil.isNotBlank(request.getUploader())) {
            boolBuilder.filter(Query.of(q -> q
                    .term(t -> t.field("uploaderName").value(request.getUploader()))
            ));
        }
        if (request.getFileTypes() != null && !request.getFileTypes().isEmpty()) {
            List<Query> typeQueries = request.getFileTypes().stream()
                    .map(type -> Query.of(q -> q.term(t -> t.field("fileType").value(type))))
                    .collect(Collectors.toList());
            boolBuilder.filter(Query.of(q -> q
                    .bool(b -> b.should(typeQueries).minimumShouldMatch("1"))
            ));
        }

        // 标签过滤
        if (StrUtil.isNotBlank(request.getTag())) {
            List<Long> taggedFileIds = docTagRepository.findFileIdsByTagName(request.getTag());
            if (taggedFileIds.isEmpty()) return new SearchResultWithTotal(List.of(), 0);
            List<Query> tagQueries = taggedFileIds.stream()
                    .map(id -> Query.of(q -> q.term(t -> t.field("id").value(id))))
                    .collect(Collectors.toList());
            boolBuilder.filter(Query.of(q -> q
                    .bool(b -> b.should(tagQueries).minimumShouldMatch("1"))
            ));
        }

        // 权限过滤
        Query permFilter = buildPermissionFilter(request);
        if (permFilter != null) {
            boolBuilder.filter(permFilter);
        }

        var searchBuilder = new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
                .index("doc_file")
                .from((request.getPage() - 1) * request.getSize())
                .size(request.getSize())
                .query(Query.of(q -> q.bool(boolBuilder.build())))
                .highlight(h -> h
                        .preTags("<em class='hl-keyword'>").postTags("</em>")
                        .maxAnalyzedOffset(-1)  // 不限高亮分析偏移，避免大文档报错
                        .fields(
                            co.elastic.clients.util.NamedValue.of("fileName",
                                co.elastic.clients.elasticsearch.core.search.HighlightField.of(f -> f)),
                            co.elastic.clients.util.NamedValue.of("fulltextContent",
                                co.elastic.clients.elasticsearch.core.search.HighlightField.of(f -> f))
                        )
                );

        try {
            var response = esClient.search(searchBuilder.build(), DocFileDocument.class);
            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            List<SearchResult> list = response.hits().hits().stream()
                    .map(hit -> {
                        DocFileDocument doc = hit.source();
                        SearchResult r = new SearchResult();
                        r.setDocumentId(doc.getId());
                        var hlFile = hit.highlight().get("fileName");
                        r.setTitle((hlFile != null && !hlFile.isEmpty()) ? hlFile.get(0) : doc.getFileName());
                        r.setUploader(doc.getUploaderName());
                        r.setFileType(doc.getFileType());
                        r.setFileSize(doc.getFileSize());
                        r.setUploadTime(doc.getCreateTime() != null ? doc.getCreateTime().toString() : null);
                        var hlContent = hit.highlight().get("fulltextContent");
                        if (hlContent != null && !hlContent.isEmpty()) {
                            r.setSummary(String.join("...", hlContent));
                            r.setHighlights(new ArrayList<>(hlContent));
                        } else if (StrUtil.isNotBlank(doc.getFulltextContent())) {
                            String content = doc.getFulltextContent();
                            r.setSummary(content.length() > 200 ? content.substring(0, 200) + "..." : content);
                            r.setHighlights(new ArrayList<>());
                        }
                        float score = (float) (100 * (1 - 1.0 / (1.0 + hit.score())));
                        r.setScore(Math.round(score * 10) / 10f);
                        r.setSpaceType(doc.getSpaceType());
                        populateTags(r);
                        return r;
                    })
                    .collect(Collectors.toList());
            return new SearchResultWithTotal(list, totalHits);
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            System.err.println("[ES-ERR] error: " + e.error());
            if (e.error() != null && e.error().rootCause() != null) {
                for (var rc : e.error().rootCause()) {
                    System.err.println("[ES-ERR] rootCause: type=" + rc.type() + ", reason=" + rc.reason());
                }
            }
            System.err.println("[ES-ERR] 完整异常信息:");
            e.printStackTrace();
            throw e;
        }
    }

    // ==================== MySQL 全文索引降级 ====================

    private SearchResultWithTotal searchByMysqlFulltext(SearchRequest request) {
        String keyword = request.getKeyword();
        if (StrUtil.isBlank(keyword)) return new SearchResultWithTotal(List.of(), 0);

        List<DocFile> files = docFileRepository.searchByKeyword(keyword);

        // 权限过滤
        files = files.stream().filter(f -> canAccessFile(f, request)).collect(Collectors.toList());

        if (StrUtil.isNotBlank(request.getUploader())) {
            files = files.stream()
                    .filter(f -> request.getUploader().equals(f.getUploaderName()))
                    .collect(Collectors.toList());
        }
        if (request.getFileTypes() != null && !request.getFileTypes().isEmpty()) {
            files = files.stream()
                    .filter(f -> request.getFileTypes().contains(f.getFileType()))
                    .collect(Collectors.toList());
        }

        // 标签过滤
        if (StrUtil.isNotBlank(request.getTag())) {
            Set<Long> taggedFileIds = new HashSet<>(docTagRepository.findFileIdsByTagName(request.getTag()));
            files = files.stream()
                    .filter(f -> taggedFileIds.contains(f.getId()))
                    .collect(Collectors.toList());
        }

        String kwLower = keyword.toLowerCase();
        List<SearchResult> fulltextResults = files.stream().map(f -> {
            SearchResult r = new SearchResult();
            r.setDocumentId(f.getId());
            // 标题关键词高亮
            String title = f.getFileName();
            boolean titleMatch = false;
            if (StrUtil.isNotBlank(title)) {
                titleMatch = title.toLowerCase().contains(kwLower);
                title = title.replaceAll("(?i)(" + java.util.regex.Pattern.quote(keyword) + ")", "<em class='hl-keyword'>$1</em>");
            }
            r.setTitle(title);
            r.setUploader(f.getUploaderName());
            r.setFileType(f.getFileType());
            r.setFileSize(f.getFileSize());
            r.setUploadTime(f.getCreateTime() != null ? f.getCreateTime().toString() : null);

            String content = f.getFulltextContent();
            List<String> highlights = new ArrayList<>();
            boolean contentMatch = false;
            int contentIdx = -1;
            if (StrUtil.isNotBlank(content)) {
                contentIdx = content.toLowerCase().indexOf(kwLower);
                contentMatch = contentIdx >= 0;
                if (contentMatch) {
                    int start = Math.max(0, contentIdx - 50);
                    int end = Math.min(content.length(), contentIdx + keyword.length() + 50);
                    String snippet = (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
                    r.setSummary(snippet);
                    // 高亮片段
                    String hlSnippet = snippet.replaceAll("(?i)(" + java.util.regex.Pattern.quote(keyword) + ")", "<em class='hl-keyword'>$1</em>");
                    highlights.add(hlSnippet);
                } else {
                    r.setSummary(content.length() > 200 ? content.substring(0, 200) + "..." : content);
                }
            }
            r.setHighlights(highlights);

            // 动态评分：标题匹配 > 内容匹配前部 > 内容匹配后部
            float score;
            if (titleMatch && contentMatch) {
                score = 85f;  // 标题+内容都匹配，最高
            } else if (titleMatch) {
                score = 75f;  // 仅标题匹配
            } else if (contentMatch) {
                // 内容匹配位置越靠前，分数越高 (50~70)
                float positionFactor = 1.0f - (float) contentIdx / Math.max(content.length(), 1);
                score = 50f + positionFactor * 20f;
            } else {
                score = 40f;  // 无匹配兜底
            }
            r.setScore(Math.round(score * 10) / 10f);
            r.setSpaceType(f.getSpaceType());
            populateTags(r);
            return r;
        }).collect(Collectors.toList());
        return new SearchResultWithTotal(fulltextResults, fulltextResults.size());
    }

    private SearchResultWithTotal searchByMysqlFilter(SearchRequest request) {
        List<DocFile> allFiles = docFileRepository.findByDeletedAndStatus(0, 1);

        // 权限过滤
        allFiles = allFiles.stream().filter(f -> canAccessFile(f, request)).collect(Collectors.toList());

        if (StrUtil.isNotBlank(request.getUploader())) {
            allFiles = allFiles.stream()
                    .filter(f -> request.getUploader().equals(f.getUploaderName()))
                    .collect(Collectors.toList());
        }
        if (request.getStartDate() != null) {
            java.time.LocalDateTime start = request.getStartDate();
            allFiles = allFiles.stream()
                    .filter(f -> f.getCreateTime() != null && !f.getCreateTime().isBefore(start))
                    .collect(Collectors.toList());
        }
        if (request.getEndDate() != null) {
            java.time.LocalDateTime end = request.getEndDate();
            allFiles = allFiles.stream()
                    .filter(f -> f.getCreateTime() != null && !f.getCreateTime().isAfter(end))
                    .collect(Collectors.toList());
        }
        if (request.getFileTypes() != null && !request.getFileTypes().isEmpty()) {
            allFiles = allFiles.stream()
                    .filter(f -> request.getFileTypes().contains(f.getFileType()))
                    .collect(Collectors.toList());
        }

        // 标签过滤
        if (StrUtil.isNotBlank(request.getTag())) {
            Set<Long> taggedFileIds = new HashSet<>(docTagRepository.findFileIdsByTagName(request.getTag()));
            allFiles = allFiles.stream()
                    .filter(f -> taggedFileIds.contains(f.getId()))
                    .collect(Collectors.toList());
        }

        List<SearchResult> filterList = allFiles.stream().map(f -> {
            SearchResult r = new SearchResult();
            r.setDocumentId(f.getId());
            r.setTitle(f.getFileName());
            r.setUploader(f.getUploaderName());
            r.setFileType(f.getFileType());
            r.setFileSize(f.getFileSize());
            r.setUploadTime(f.getCreateTime() != null ? f.getCreateTime().toString() : null);
            r.setScore(0f);
            r.setHighlights(new ArrayList<>());
            // 显示文件内容摘要
            String content = f.getFulltextContent();
            if (StrUtil.isNotBlank(content)) {
                r.setSummary(content.length() > 200 ? content.substring(0, 200) + "..." : content);
            }
            r.setSpaceType(f.getSpaceType());
            populateTags(r);
            return r;
        }).collect(Collectors.toList());
        return new SearchResultWithTotal(filterList, filterList.size());
    }

    // ==================== 关键词自动补全 ====================

    @Override
    public List<SearchSuggestion> suggestKeywords(String prefix) {
        if (StrUtil.isBlank(prefix)) return List.of();

        List<String> historyKeywords = searchKeywordMapper.findByPrefix(prefix, 5);

        List<String> docKeywords;
        try {
            docKeywords = suggestFromEs(prefix);
        } catch (Exception e) {
            docKeywords = suggestFromMysql(prefix);
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(historyKeywords);
        merged.addAll(docKeywords);

        List<SearchSuggestion> result = new ArrayList<>();
        int rank = 0;
        for (String kw : merged) {
            if (rank >= 10) break;
            boolean isHistory = historyKeywords.contains(kw);
            boolean isHot = isHistory && searchKeywordMapper.findHotKeywords(10).contains(kw);
            result.add(new SearchSuggestion(kw, isHistory ? 1 : 0, isHot,
                    isHistory ? "history" : "document"));
            rank++;
        }
        return result;
    }

    private List<String> suggestFromEs(String prefix) {
        Query prefixQuery = Query.of(q -> q
                .prefix(p -> p.field("fileName").value(prefix))
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(prefixQuery)
                .withPageable(PageRequest.of(0, 10))
                .build();

        SearchHits<DocFileDocument> hits = elasticsearchOperations.search(nativeQuery, DocFileDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getFileName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> suggestFromMysql(String prefix) {
        return docFileRepository.findByDeletedAndStatus(0, 1).stream()
                .map(DocFile::getFileName)
                .filter(name -> name != null && name.toLowerCase().startsWith(prefix.toLowerCase()))
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    // ==================== 热词 ====================

    @Override
    public List<String> getHotKeywords() {
        try {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(HOT_KEYWORDS_ZSET_KEY, 0, 9);
            if (tuples != null && !tuples.isEmpty()) {
                return tuples.stream()
                        .map(tuple -> tuple.getValue() != null ? tuple.getValue().toString() : "")
                        .filter(kw -> !kw.isEmpty())
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            System.err.println("[Redis缓存] 读取搜索热词ZSET失败: " + e.getMessage());
        }
        return searchKeywordMapper.findHotKeywords(10);
    }

    @Override
    @Transactional
    public void recordKeyword(String keyword, Long userId) {
        if (StrUtil.isBlank(keyword)) return;
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORDS_ZSET_KEY, keyword, 1);
        } catch (Exception e) {
            System.err.println("[Redis缓存] 写入搜索热词ZSET失败: " + e.getMessage());
        }
        int updated = searchKeywordMapper.incrementCount(keyword);
        if (updated == 0) {
            SearchKeyword sk = new SearchKeyword();
            sk.setKeyword(keyword);
            sk.setSearchCount(1);
            sk.setUserId(userId);
            sk.setIsHot(0);
            searchKeywordMapper.save(sk);
        }
    }

    // ==================== ES 索引管理 ====================

    @Override
    public List<DocumentIndex> getDocumentsByDepartment(Long departmentId) {
        try {
            return docFileSearchRepository.findByDepartmentId(departmentId).stream()
                    .map(esDoc -> {
                        DocumentIndex di = new DocumentIndex();
                        di.setDocumentId(esDoc.getId());
                        di.setTitle(esDoc.getFileName());
                        di.setUploader(esDoc.getUploaderName());
                        di.setFileType(esDoc.getFileType());
                        di.setFileSize(esDoc.getFileSize());
                        di.setDepartmentId(esDoc.getDepartmentId());
                        return di;
                    }).collect(Collectors.toList());
        } catch (Exception e) {
            return docFileRepository.findByDeletedAndStatus(0, 1).stream()
                    .filter(f -> departmentId.equals(f.getDepartmentId()))
                    .map(f -> {
                        DocumentIndex di = new DocumentIndex();
                        di.setDocumentId(f.getId());
                        di.setTitle(f.getFileName());
                        di.setUploader(f.getUploaderName());
                        di.setFileType(f.getFileType());
                        di.setFileSize(f.getFileSize());
                        di.setDepartmentId(f.getDepartmentId());
                        return di;
                    }).collect(Collectors.toList());
        }
    }

    // ==================== 工具方法 ====================

    private String getHighlightField(SearchHit<DocFileDocument> hit, String field) {
        var fragments = hit.getHighlightField(field);
        if (fragments != null && !fragments.isEmpty()) {
            return fragments.get(0);
        }
        return null;
    }

    private List<String> getHighlightFieldList(SearchHit<DocFileDocument> hit, String field) {
        var fragments = hit.getHighlightField(field);
        return fragments != null ? fragments : List.of();
    }

    /** 为搜索结果填充标签列表 */
    private void populateTags(SearchResult result) {
        try {
            if (result.getDocumentId() == null) return;
            List<DocTag> tags = docTagRepository.findByFileId(result.getDocumentId());
            if (tags != null && !tags.isEmpty()) {
                result.setTags(tags.stream()
                        .map(DocTag::getTagName)
                        .collect(Collectors.toList()));
            }
        } catch (Exception ignored) {
            // 标签填充失败不影响搜索结果
        }
    }
}
