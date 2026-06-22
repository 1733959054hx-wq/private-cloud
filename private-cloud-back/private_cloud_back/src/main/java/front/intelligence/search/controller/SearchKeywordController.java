package front.intelligence.search.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.intelligence.search.entity.SearchKeyword;
import front.intelligence.search.service.SearchKeywordService;
import front.workspace.documentspace.dto.FileDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/front/search")
public class SearchKeywordController {

    @Autowired
    private SearchKeywordService searchKeywordService;

    @GetMapping
    public Result<List<FileDTO>> search(@RequestParam String keyword, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<FileDTO> results = searchKeywordService.search(keyword, userId);
        return Result.success(results);
    }

    @GetMapping("/fulltext")
    public Result<List<FileDTO>> fulltextSearch(@RequestParam String keyword, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<FileDTO> results = searchKeywordService.fulltextSearch(keyword, userId);
        return Result.success(results);
    }

    @GetMapping("/hot")
    public Result<List<SearchKeyword>> getHotKeywords() {
        List<SearchKeyword> keywords = searchKeywordService.getHotKeywords();
        return Result.success(keywords);
    }

    @GetMapping("/history")
    public Result<List<SearchKeyword>> getSearchHistory(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<SearchKeyword> keywords = searchKeywordService.getUserSearchHistory(userId);
        return Result.success(keywords);
    }

    @PutMapping("/{keywordId}/hot")
    public Result<Void> markAsHot(@PathVariable Long keywordId) {
        searchKeywordService.markAsHot(keywordId);
        return Result.success();
    }
}
