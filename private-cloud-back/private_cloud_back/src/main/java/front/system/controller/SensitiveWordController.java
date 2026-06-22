package front.system.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.system.entity.SysSensitiveWord;
import front.system.service.SensitiveWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/sensitive-words")
public class SensitiveWordController {

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @GetMapping
    public Result<List<SysSensitiveWord>> getAllWords() {
        List<SysSensitiveWord> words = sensitiveWordService.getAllWords();
        return Result.success(words);
    }

    @GetMapping("/search")
    public Result<List<SysSensitiveWord>> searchWords(@RequestParam String keyword) {
        List<SysSensitiveWord> words = sensitiveWordService.searchWords(keyword);
        return Result.success(words);
    }

    @GetMapping("/category/{category}")
    public Result<List<SysSensitiveWord>> getWordsByCategory(@PathVariable String category) {
        List<SysSensitiveWord> words = sensitiveWordService.getWordsByCategory(category);
        return Result.success(words);
    }

    @PostMapping
    public Result<SysSensitiveWord> addWord(@RequestBody Map<String, Object> body,
                                             Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        String word = (String) body.get("word");
        String category = (String) body.get("category");
        Integer level = body.get("level") != null ? Integer.valueOf(body.get("level").toString()) : 1;
        try {
            SysSensitiveWord saved = sensitiveWordService.addWord(word, category, level, userId);
            return Result.success(saved);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/batch")
    public Result<Void> batchAddWords(@RequestBody Map<String, Object> body,
                                       Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        @SuppressWarnings("unchecked")
        List<String> words = (List<String>) body.get("words");
        String category = (String) body.get("category");
        Integer level = body.get("level") != null ? Integer.valueOf(body.get("level").toString()) : 1;
        if (words == null || words.isEmpty()) {
            return Result.error("词语列表不能为空");
        }
        try {
            sensitiveWordService.batchAddWords(words, category, level, userId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<SysSensitiveWord> updateWord(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        String word = (String) body.get("word");
        String category = (String) body.get("category");
        Integer level = body.get("level") != null ? Integer.valueOf(body.get("level").toString()) : null;
        Integer enabled = body.get("enabled") != null ? Integer.valueOf(body.get("enabled").toString()) : null;
        try {
            SysSensitiveWord updated = sensitiveWordService.updateWord(id, word, category, level, enabled);
            return Result.success(updated);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteWord(@PathVariable Long id) {
        sensitiveWordService.deleteWord(id);
        return Result.success();
    }

    @PostMapping("/check")
    public Result<Map<String, Object>> checkText(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isEmpty()) {
            return Result.error("文本不能为空");
        }
        boolean contains = sensitiveWordService.containsSensitiveWord(text);
        List<String> foundWords = sensitiveWordService.findSensitiveWords(text);
        String filtered = sensitiveWordService.filterSensitiveWord(text);
        return Result.success(Map.of(
                "contains", contains,
                "sensitiveWords", foundWords,
                "filteredText", filtered
        ));
    }

    @PostMapping("/rebuild")
    public Result<Void> rebuildDfaMap() {
        sensitiveWordService.rebuildDfaMap();
        return Result.success();
    }
}
