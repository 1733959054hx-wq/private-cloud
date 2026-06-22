package front.intelligence.preview.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.intelligence.preview.dto.ProgressDTO;
import front.intelligence.preview.service.DocProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/front/preview/progress")
public class DocProgressController {

    @Autowired
    private DocProgressService progressService;

    @PostMapping("/save")
    public Result<?> saveProgress(@RequestBody ProgressDTO dto, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        progressService.saveOrUpdateProgress(userId, dto);
        return Result.success("进度保存成功");
    }

    @GetMapping("/get")
    public Result<Double> getProgress(@RequestParam Long fileId, @RequestParam Integer progressType, Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        Double progress = progressService.getProgress(userId, fileId, progressType);
        return Result.success(progress);
    }
}
