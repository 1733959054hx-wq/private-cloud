package front.workspace.personalworkspace.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.workspace.personalworkspace.dto.DashboardVO;
import front.workspace.personalworkspace.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping
    public Result<DashboardVO> getDashboard(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        DashboardVO dashboard = dashboardService.getDashboardData(userId);
        return Result.success(dashboard);
    }

    /**
     * 获取所在部门空间的最新动态/通知
     * GET /api/front/dashboard/team-updates
     */
    @GetMapping("/team-updates")
    public Result<List<Map<String, Object>>> getTeamUpdates(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<Map<String, Object>> updates = dashboardService.getTeamUpdates(userId);
        return Result.success(updates);
    }
}
