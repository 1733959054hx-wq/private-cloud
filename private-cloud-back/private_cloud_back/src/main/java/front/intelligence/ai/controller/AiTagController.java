package front.intelligence.ai.controller;

import front.hxconfig.Result;
import front.intelligence.ai.entity.DocMetadata;
import front.intelligence.ai.entity.DocTag;
import front.intelligence.ai.service.AiTagService;
import front.intelligence.ai.service.DocTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/ai/tags")
public class AiTagController {

    @Autowired
    private AiTagService aiTagService;

    @Autowired
    private DocTagService docTagService;

    @GetMapping("/{fileId}")
    public Result<List<DocMetadata>> getFileTags(@PathVariable Long fileId) {
        try {
            List<DocMetadata> tags = aiTagService.getFileTags(fileId);
            return Result.success(tags);
        } catch (Exception e) {
            return Result.error(500, "AI标签提取后台处理中或暂时失败，等待重试");
        }
    }

    @PostMapping("/{fileId}")
    public Result<Map<String, Object>> triggerTagExtraction(@PathVariable Long fileId) {
        try {
            aiTagService.extractTagsAsync(fileId);

            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileId);
            result.put("status", "PROCESSING");
            result.put("message", "AI标签提取任务已提交");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(500, "AI标签提取任务提交失败，请稍后重试");
        }
    }

    @PostMapping("/{fileId}/re-extract")
    public Result<Map<String, Object>> reExtractTags(@PathVariable Long fileId) {
        try {
            aiTagService.reExtractTags(fileId);

            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileId);
            result.put("status", "PROCESSING");
            result.put("message", "AI标签重新提取任务已提交");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(500, "AI标签重新提取任务提交失败，请稍后重试");
        }
    }

    @PostMapping("/{fileId}/confirm")
    public Result<Map<String, Object>> confirmTags(@PathVariable Long fileId) {
        aiTagService.confirmTags(fileId);

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("status", "CONFIRMED");
        result.put("message", "AI标签已确认添加");
        return Result.success(result);
    }

    @PostMapping("/{fileId}/dismiss")
    public Result<Map<String, Object>> dismissTags(@PathVariable Long fileId) {
        aiTagService.dismissTags(fileId);

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("status", "DISMISSED");
        result.put("message", "AI标签已丢弃");
        return Result.success(result);
    }

    @GetMapping("/{fileId}/doc-tags")
    public Result<List<DocTag>> getDocTags(@PathVariable Long fileId) {
        List<DocTag> tags = docTagService.getFileTags(fileId);
        return Result.success(tags);
    }

    @PostMapping("/{fileId}/doc-tags")
    public Result<DocTag> addManualTag(@PathVariable Long fileId, @RequestBody Map<String, String> body) {
        String tagName = body.get("tagName");
        DocTag tag = docTagService.addManualTag(fileId, tagName);
        return Result.success(tag);
    }

    @DeleteMapping("/doc-tags/{tagId}")
    public Result<Void> removeTag(@PathVariable Long tagId) {
        docTagService.removeTag(tagId);
        return Result.success();
    }

    @PutMapping("/doc-tags/{tagId}")
    public Result<DocTag> updateTagName(@PathVariable Long tagId, @RequestBody Map<String, String> body) {
        String tagName = body.get("tagName");
        DocTag tag = docTagService.updateTagName(tagId, tagName);
        return Result.success(tag);
    }

    @GetMapping("/by-name")
    public Result<List<DocTag>> getTagsByName(@RequestParam String tagName) {
        List<DocTag> tags = docTagService.getTagsByName(tagName);
        return Result.success(tags);
    }

    /** 获取标签云数据（用于搜索页的标签云展示） */
    @GetMapping("/cloud")
    public Result<List<Map<String, Object>>> getTagCloud() {
        List<Map<String, Object>> cloud = docTagService.getTagCloud();
        return Result.success(cloud);
    }

    /** 按标签名搜索文件ID列表 */
    @GetMapping("/file-ids")
    public Result<List<Long>> getFileIdsByTag(@RequestParam String tagName) {
        List<Long> fileIds = docTagService.getFileIdsByTagName(tagName);
        return Result.success(fileIds);
    }
}