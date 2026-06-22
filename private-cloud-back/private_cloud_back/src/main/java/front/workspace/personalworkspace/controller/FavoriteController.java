package front.workspace.personalworkspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.workspace.personalworkspace.dto.FavoriteVO;
import front.workspace.personalworkspace.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping
    public Result<List<FavoriteVO>> getUserFavorites(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<FavoriteVO> favorites = favoriteService.getUserFavorites(userId);
        return Result.success(favorites);
    }

    @PostMapping
    public Result<FavoriteVO> addFavorite(Authentication authentication, @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        Integer targetType = Integer.valueOf(body.get("targetType").toString());
        FavoriteVO favorite = favoriteService.addFavorite(userId, targetId, targetType);
        return Result.success(favorite);
    }

    @DeleteMapping
    public Result<Void> removeFavorite(Authentication authentication,
                                        @RequestParam Long targetId,
                                        @RequestParam Integer targetType) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        favoriteService.removeFavorite(userId, targetId, targetType);
        return Result.success();
    }

    @GetMapping("/check")
    public Result<Map<String, Boolean>> checkFavorite(Authentication authentication,
                                                        @RequestParam Long targetId,
                                                        @RequestParam Integer targetType) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        boolean favorited = favoriteService.isFavorited(userId, targetId, targetType);
        Map<String, Boolean> result = new java.util.HashMap<>();
        result.put("favorited", favorited);
        return Result.success(result);
    }
}
