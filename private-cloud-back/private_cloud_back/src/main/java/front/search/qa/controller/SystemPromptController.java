package front.search.qa.controller;

import front.hxconfig.AuthUtil;
import front.search.qa.entity.SystemPrompt;
import front.search.qa.service.SystemPromptService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统提示词模板管理控制器
 */
@RestController
@RequestMapping("/api/front/ai/prompts")
public class SystemPromptController {

    @Resource
    private SystemPromptService systemPromptService;

    /**
     * 获取当前用户可用的提示词模板（预设 + 用户自建）
     */
    @GetMapping
    public Map<String, Object> getPrompts(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        List<SystemPrompt> prompts = systemPromptService.getAvailablePrompts(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", prompts);
        return result;
    }

    /**
     * 创建自定义提示词模板
     */
    @PostMapping
    public Map<String, Object> createPrompt(@RequestBody SystemPrompt prompt, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        prompt.setUserId(userId);
        SystemPrompt created = systemPromptService.createPrompt(prompt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", created);
        return result;
    }

    /**
     * 更新自定义提示词模板
     */
    @PutMapping("/{id}")
    public Map<String, Object> updatePrompt(@PathVariable Long id, @RequestBody SystemPrompt prompt, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        SystemPrompt updated = systemPromptService.updatePrompt(id, prompt, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", updated);
        return result;
    }

    /**
     * 删除自定义提示词模板
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deletePrompt(@PathVariable Long id, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        systemPromptService.deletePrompt(id, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        return result;
    }
}
