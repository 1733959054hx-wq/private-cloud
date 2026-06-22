package front.search.engine.controller;

import front.hxconfig.AuthUtil;
import front.search.engine.dto.SearchRequest;
import front.search.engine.dto.SearchResult;
import front.search.engine.dto.SearchResultWithTotal;
import front.search.engine.dto.SearchSuggestion;
import front.search.engine.entity.DocumentIndex;
import front.search.engine.es.DocFileIndexService;
import front.search.engine.service.SearchService;
import front.system.entity.SysUser;
import front.system.repository.SysUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索引擎控制器
 */
@RestController
@RequestMapping("/api/search/engine")
public class SearchController {

    @Resource
    private SearchService searchService;

    @Resource
    private DocFileIndexService docFileIndexService;

    @Resource
    private SysUserRepository sysUserRepository;

    /** 注入当前用户身份到请求上下文 */
    private void injectUserContext(SearchRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!AuthUtil.isAuthenticated(auth)) return;

        Long userId = AuthUtil.getUserId(auth);
        request.setCurrentUserId(userId);

        // 检查是否是管理员
        boolean isAdmin = false;
        if (auth.getAuthorities() != null) {
            for (GrantedAuthority ga : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(ga.getAuthority()) || "ROLE_DEPT_ADMIN".equals(ga.getAuthority())) {
                    isAdmin = true;
                    break;
                }
            }
        }
        request.setCurrentIsAdmin(isAdmin);

        // 获取用户部门ID
        if (userId != null) {
            try {
                java.util.Optional<SysUser> userOpt = sysUserRepository.findById(userId);
                userOpt.ifPresent(u -> request.setCurrentDepartmentId(u.getDepartmentId()));
            } catch (Exception ignored) {}
        }
    }

    /**
     * 全文检索（关键词 + 多条件筛选 + 分页）
     * POST /api/search/engine/search
     */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody SearchRequest request) {
        injectUserContext(request);
        SearchResultWithTotal wrapper = searchService.fullTextSearch(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("data", wrapper.getList());
        result.put("total", wrapper.getTotal());
        result.put("page", request.getPage());
        result.put("size", request.getSize());
        return result;
    }

    /**
     * 关键词自动补全
     * GET /api/search/engine/suggest?prefix=关键词前缀
     */
    @GetMapping("/suggest")
    public Map<String, Object> suggest(@RequestParam String prefix) {
        List<SearchSuggestion> suggestions = searchService.suggestKeywords(prefix);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", suggestions);
        return result;
    }

    /**
     * 热门搜索词
     * GET /api/search/engine/hot
     */
    @GetMapping("/hot")
    public Map<String, Object> hotKeywords() {
        List<String> keywords = searchService.getHotKeywords();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", keywords);
        return result;
    }

    /**
     * 根据部门ID查询文档
     * GET /api/search/engine/department?deptId=1
     */
    @GetMapping("/department")
    public Map<String, Object> getByDepartment(@RequestParam Long deptId) {
        List<DocumentIndex> docs = searchService.getDocumentsByDepartment(deptId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", docs);
        return result;
    }

    /**
     * 手动重建 ES 索引
     * POST /api/search/engine/index/rebuild
     */
    @PostMapping("/index/rebuild")
    public Map<String, Object> rebuildIndex() {
        docFileIndexService.rebuildIndex();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "ES 索引重建完成");
        return result;
    }
}
