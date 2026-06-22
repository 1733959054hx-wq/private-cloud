package front.workspace.documentspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.workspace.documentspace.entity.RecycleBin;
import front.workspace.documentspace.service.RecycleBinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/recycle-bin")
public class RecycleBinController {

    @Autowired
    private RecycleBinService recycleBinService;

    @GetMapping
    public Result<List<RecycleBin>> getRecycleBinItems(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<RecycleBin> items = recycleBinService.getRecycleBinItems(userId);
        return Result.success(items);
    }

    @PostMapping("/move")
    public Result<Void> moveToRecycleBin(Authentication authentication,
                                          @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        String itemType = body.get("itemType").toString();
        Long itemId = Long.valueOf(body.get("itemId").toString());
        recycleBinService.moveToRecycleBin(itemType, itemId, userId);
        return Result.success();
    }

    @PostMapping("/batch-move")
    public Result<Void> batchMoveToRecycleBin(Authentication authentication,
                                               @RequestBody List<Map<String, Object>> items) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        for (Map<String, Object> item : items) {
            String itemType = item.get("itemType").toString();
            Long itemId = Long.valueOf(item.get("itemId").toString());
            recycleBinService.moveToRecycleBin(itemType, itemId, userId);
        }
        return Result.success();
    }

    @PostMapping("/restore")
    public Result<Void> restoreItem(@RequestBody Map<String, Object> body) {
        String itemType = body.get("itemType").toString();
        Long itemId = Long.valueOf(body.get("itemId").toString());
        recycleBinService.restoreItem(itemType, itemId);
        return Result.success();
    }

    @DeleteMapping("/permanent")
    public Result<Void> permanentlyDelete(@RequestBody Map<String, Object> body) {
        String itemType = body.get("itemType").toString();
        Long itemId = Long.valueOf(body.get("itemId").toString());
        recycleBinService.permanentlyDelete(itemType, itemId);
        return Result.success();
    }

    @DeleteMapping
    public Result<Void> emptyRecycleBin(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        recycleBinService.emptyRecycleBin(userId);
        return Result.success();
    }
}
