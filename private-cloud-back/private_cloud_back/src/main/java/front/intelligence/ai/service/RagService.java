package front.intelligence.ai.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import front.search.engine.es.DocFileDocument;
import front.system.entity.SysUser;
import front.system.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务
 * 从 Elasticsearch 检索相关文档 → 构建上下文
 */
@Service
public class RagService {

    @Autowired(required = false)
    private ElasticsearchOperations elasticsearchOperations;

    @Resource
    private SysUserRepository sysUserRepository;

    @Resource
    private front.workspace.documentspace.repository.DocFileRepository docFileRepository;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String RAG_CACHE_PREFIX = "rag:retrieval:";
    private static final long RAG_CACHE_TTL_SECONDS = 300;

    /** 检索结果中的单条引用来源 */
    public static class Reference {
        private long documentId;
        private String title;
        private String snippet;
        private float score;

        public long getDocumentId() { return documentId; }
        public void setDocumentId(long documentId) { this.documentId = documentId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
    }

    /**
     * 检索与问题相关的文档（带空间权限过滤）
     */
    public List<Reference> searchRelevantDocs(String question, Long userId, Long departmentId, int limit) {
        if (elasticsearchOperations == null) return List.of();
        try {
            String cacheKey = buildCacheKey(question, userId, departmentId, limit);
            if (stringRedisTemplate != null) {
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    try {
                        return JSON.parseObject(cached, new TypeReference<List<Reference>>() {});
                    } catch (Exception e) {
                        System.err.println("[RAG] 缓存解析失败: " + e.getMessage());
                    }
                }
            }

            var boolBuilder = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
            boolBuilder.must(Query.of(q -> q
                    .multiMatch(m -> m
                            .fields("fileName^3", "fulltextContent")
                            .query(question)
                    )
            ));

            // 过滤签章文件
            boolBuilder.mustNot(Query.of(q -> q
                    .match(m -> m.field("fulltextContent").query("签章PDF：内容不可读"))
            ));

            // 空间权限过滤
            if (userId != null && sysUserRepository != null) {
                var userOpt = sysUserRepository.findById(userId);
                if (userOpt.isPresent()) {
                    SysUser user = userOpt.get();
                    Long deptId = user.getDepartmentId();
                    var spaceFilter = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
                    // 个人空间
                    spaceFilter.should(Query.of(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t.field("spaceType").value(0)))
                            .must(m -> m.term(t -> t.field("spaceId").value(userId)))
                    )));
                    // 部门空间 + 企业空间
                    if (deptId != null) {
                        spaceFilter.should(Query.of(q -> q.bool(b -> b
                                .must(m -> m.terms(t -> t.field("spaceType").terms(t2 -> t2.value(List.of(
                                        FieldValue.of(fv -> fv.longValue(1)),
                                        FieldValue.of(fv -> fv.longValue(2))
                                )))))
                                .must(m -> m.term(t -> t.field("departmentId").value(deptId)))
                        )));
                    }
                    boolBuilder.filter(Query.of(q -> q.bool(spaceFilter.build())));
                }
            }

            if (departmentId != null) {
                boolBuilder.filter(Query.of(q -> q
                        .term(t -> t.field("departmentId").value(departmentId))
                ));
            }

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.bool(boolBuilder.build())))
                    .withPageable(PageRequest.of(0, limit))
                    .build();

            SearchHits<DocFileDocument> searchHits = elasticsearchOperations.search(nativeQuery, DocFileDocument.class);

            List<Reference> result = searchHits.getSearchHits().stream().map(hit -> {
                DocFileDocument doc = hit.getContent();
                Reference ref = new Reference();
                ref.setDocumentId(doc.getId());
                ref.setTitle(doc.getFileName());
                // snippet 只用于前端展示引用来源，截断防止序列化 OOM
                String fullContent = doc.getFulltextContent();
                if (fullContent != null && fullContent.length() < 500) {
                    ref.setSnippet(fullContent);
                } else {
                    String mysqlContent = docFileRepository.findById(doc.getId())
                            .map(f -> f.getFulltextContent() != null ? f.getFulltextContent() : "")
                            .orElse(fullContent != null ? fullContent : "");
                    ref.setSnippet(mysqlContent.length() > 500 ? mysqlContent.substring(0, 500) + "..." : mysqlContent);
                }
                float score = (float) (100 * (1 - 1.0 / (1.0 + hit.getScore())));
                ref.setScore(Math.round(score * 10) / 10f);
                return ref;
            }).collect(Collectors.toList());

            if (stringRedisTemplate != null) {
                try {
                    stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result),
                            RAG_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.err.println("[RAG] 写入缓存失败: " + e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildCacheKey(String question, Long userId, Long departmentId, int limit) {
        String raw = question.trim().toLowerCase() + "|" + userId + "|" + departmentId + "|" + limit;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return RAG_CACHE_PREFIX + sb.toString();
        } catch (Exception e) {
            return RAG_CACHE_PREFIX + raw.hashCode();
        }
    }

    /**
     * 构建 AI 上下文 prompt
     */
    public String buildContext(List<Reference> references) {
        if (references == null || references.isEmpty()) return "";
        final int MAX_CONTEXT_PER_DOC = 4000;
        final int MAX_TOTAL_CONTEXT = 15000;
        StringBuilder sb = new StringBuilder("以下是公司内部文档中与问题相关的资料。请仔细阅读这些文档内容，并基于文档来回答用户的问题：\n\n");
        for (int i = 0; i < references.size(); i++) {
            Reference ref = references.get(i);
            sb.append("【来源文档 ").append(i + 1).append("：").append(ref.getTitle()).append("】\n");
            // 从 DB 获取完整内容作为 AI 上下文，不依赖截断后的 snippet
            String content = docFileRepository.findById(ref.getDocumentId())
                    .map(f -> f.getFulltextContent() != null ? f.getFulltextContent() : "")
                    .orElse(ref.getSnippet() != null ? ref.getSnippet() : "");
            if (content.length() > MAX_CONTEXT_PER_DOC) {
                content = content.substring(0, MAX_CONTEXT_PER_DOC) + "...";
            }
            sb.append(content).append("\n\n");
            if (sb.length() > MAX_TOTAL_CONTEXT) {
                sb.append("...(参考文档过多，已截断)\n");
                break;
            }
        }
        sb.append("请基于以上文档内容回答用户问题，引用时标注来源（如【来源：文档名称】）。");
        return sb.toString();
    }

    private String truncateContent(String content, int maxLen) {
        if (content == null) return "";
        return content.length() > maxLen ? content.substring(0, maxLen) + "..." : content;
    }
}
