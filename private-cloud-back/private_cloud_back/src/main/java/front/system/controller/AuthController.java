package front.system.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.JwtUtil;
import front.hxconfig.RsaKeyManager;
import front.hxconfig.Result;
import front.system.entity.SysUser;
import front.system.repository.SysDepartmentRepository;
import front.system.service.CaptchaService;
import front.system.service.SysUserService;
import front.system.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/front/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysDepartmentRepository departmentRepository;

    @Autowired
    private RsaKeyManager rsaKeyManager;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 连续登录失败最大次数，超过则锁定 */
    @Value("${app.login.max-fail-count:5}")
    private int maxFailCount;

    /** 账号锁定时长（秒），默认15分钟 */
    @Value("${app.login.lock-seconds:900}")
    private int lockSeconds;

    private static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";

    // ======================== RSA公钥接口 ========================

    /**
     * 获取RSA公钥（Base64编码），用于前端加密密码
     */
    @GetMapping("/rsa-public-key")
    public Result<Map<String, String>> getRsaPublicKey() {
        Map<String, String> result = new HashMap<>();
        result.put("publicKey", rsaKeyManager.getPublicKeyBase64());
        return Result.success(result);
    }

    // ======================== 验证码接口 ========================

    /**
     * 获取点选文字验证码
     * 返回：captchaKey, image(Base64 PNG), prompt(提示文字)
     */
    @GetMapping("/captcha")
    public Result<Map<String, Object>> getCaptcha() {
        Map<String, Object> captchaData = captchaService.generateCaptcha();
        return Result.success(captchaData);
    }

    // ======================== 登录接口 ========================

    /**
     * 登录接口（增强版）
     * 请求体：
     * {
     *   "username": "xxx",
     *   "encryptedPassword": "RSA加密后的Base64密码",
     *   "captchaKey": "验证码标识UUID",
     *   "captchaClicks": [{"x": 120, "y": 80}, {"x": 200, "y": 60}, {"x": 160, "y": 100}]
     * }
     *
     * 处理流程：
     * 1. 验证点选文字验证码
     * 2. RSA私钥解密密码
     * 3. Redis检查账号是否被锁定
     * 4. BCrypt密码比对
     * 5. 成功→清除失败计数返回Token；失败→累加计数并检查是否触发锁定
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, Object> loginRequest,
                                             HttpServletRequest request) {
        String username = (String) loginRequest.get("username");
        String encryptedPassword = (String) loginRequest.get("encryptedPassword");
        String captchaKey = (String) loginRequest.get("captchaKey");
        Object captchaClicksObj = loginRequest.get("captchaClicks");

        // ========== 参数校验 ==========
        if (username == null || username.isBlank()) {
            return Result.error(400, "用户名不能为空");
        }
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return Result.error(400, "密码不能为空");
        }
        if (captchaKey == null || captchaKey.isBlank()) {
            return Result.error(400, "请完成验证码验证");
        }

        // ========== 1. 验证点选文字验证码 ==========
        if (captchaClicksObj == null) {
            return Result.error(400, "验证码数据不完整");
        }
        List<Map<String, Object>> clicks;
        try {
            clicks = (List<Map<String, Object>>) captchaClicksObj;
            if (clicks == null || clicks.isEmpty()) {
                return Result.error(400, "验证码数据不完整");
            }
        } catch (ClassCastException e) {
            return Result.error(400, "验证码数据格式错误");
        }

        if (!captchaService.verifyCaptcha(captchaKey, clicks)) {
            return Result.error(400, "验证码错误，请重试");
        }

        // ========== 2. RSA解密密码 ==========
        String plainPassword;
        try {
            plainPassword = rsaKeyManager.decrypt(encryptedPassword);
        } catch (Exception e) {
            return Result.error(400, "密码解密失败，请刷新页面重试");
        }

        // ========== 3. 检查Redis账号锁定状态 ==========
        String failKey = LOGIN_FAIL_KEY_PREFIX + username;
        Object failCountObj = redisTemplate.opsForValue().get(failKey);
        int failCount = 0;
        if (failCountObj != null) {
            failCount = failCountObj instanceof Integer
                    ? (Integer) failCountObj
                    : Integer.parseInt(failCountObj.toString());
            if (failCount >= maxFailCount) {
                Long ttl = redisTemplate.getExpire(failKey, TimeUnit.SECONDS);
                String ttlMsg;
                if (ttl != null && ttl > 0) {
                    long minutes = (ttl + 59) / 60;
                    ttlMsg = String.format("账号已锁定，请%d分钟后再试", minutes);
                } else {
                    ttlMsg = "账号已锁定，请稍后再试";
                }
                return Result.error(423, ttlMsg);
            }
        }

        // ========== 4. 用户验证 ==========
        try {
            SysUser user = sysUserService.findByUsername(username);

            // 检查账号状态
            if (user.getStatus() != 1) {
                // 审计日志：账号已禁用
                auditLogService.logAsync(AuditLogService.MODULE_AUTH, AuditLogService.OP_LOGIN,
                        "user", user.getId(), "登录失败：账号已禁用", AuditLogService.STATUS_FAIL,
                        user.getId(), username);
                return Result.error(403, "账号已被禁用");
            }

            // 密码比对（优先从独立凭证表读取，兼容旧数据自动迁移）
            String storedPassword = sysUserService.migratePasswordIfNeeded(user);
            if (storedPassword == null || !passwordEncoder.matches(plainPassword, storedPassword)) {
                // 密码错误 → 累加失败计数
                failCount++;
                redisTemplate.opsForValue().set(failKey, failCount, lockSeconds, TimeUnit.SECONDS);

                // 审计日志：密码错误
                auditLogService.logAsync(AuditLogService.MODULE_AUTH, AuditLogService.OP_LOGIN,
                        "user", user.getId(), "登录失败：密码错误，第" + failCount + "次",
                        AuditLogService.STATUS_FAIL, user.getId(), username);

                int remaining = maxFailCount - failCount;
                if (failCount >= maxFailCount) {
                    return Result.error(423,
                            String.format("连续登录失败%d次，账号已锁定%d分钟", maxFailCount, lockSeconds / 60));
                } else {
                    return Result.error(401,
                            String.format("用户名或密码错误，还剩%d次尝试机会", remaining));
                }
            }

            // ========== 5. 登录成功 → 清除失败计数、记录审计信息 ==========
            redisTemplate.delete(failKey);

            // 记录最后登录时间与IP
            String clientIp = getClientIp(request);
            sysUserService.recordLoginInfo(user.getId(), clientIp);

            // 审计日志：登录成功
            auditLogService.logAsync(AuditLogService.MODULE_AUTH, AuditLogService.OP_LOGIN,
                    "user", user.getId(), "用户登录成功（IP: " + clientIp + "）", AuditLogService.STATUS_SUCCESS,
                    user.getId(), username);

            List<String> roleCodes = sysUserService.getUserRoleCodes(user.getId());
            List<String> permissionCodes = sysUserService.getUserPermissionCodes(user.getId());

            String roleStr = roleCodes.isEmpty() ? "user" : String.join(",", roleCodes);
            String token = jwtUtil.generateToken(user.getId(), username, roleStr);
            System.out.println("[登录成功] userId=" + user.getId() + " username=" + username + " token=" + token);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userId", user.getId());
            result.put("username", user.getUsername());
            result.put("realName", user.getRealName());
            result.put("departmentId", user.getDepartmentId());
            if (user.getDepartmentId() != null) {
                departmentRepository.findById(user.getDepartmentId())
                        .ifPresent(dept -> {
                            result.put("departmentName", dept.getDeptName());
                            if (dept.getCompanyId() != null) {
                                result.put("companyId", dept.getCompanyId());
                            }
                        });
            }
            result.put("roles", roleCodes);
            result.put("permissions", permissionCodes);
            return Result.success(result);

        } catch (RuntimeException e) {
            // 用户不存在也计入失败（防止用户名枚举攻击）
            failCount++;
            redisTemplate.opsForValue().set(failKey, failCount, lockSeconds, TimeUnit.SECONDS);

            // 审计日志：用户不存在（与密码错误区分，便于安全分析）
            String failReason = "登录失败：用户不存在，第" + failCount + "次";
            auditLogService.logAsync(AuditLogService.MODULE_AUTH, AuditLogService.OP_LOGIN,
                    "user", null, failReason, AuditLogService.STATUS_FAIL, null, username);

            int remaining = maxFailCount - failCount;
            if (failCount >= maxFailCount) {
                return Result.error(423,
                        String.format("连续登录失败%d次，账号已锁定%d分钟", maxFailCount, lockSeconds / 60));
            } else {
                return Result.error(401,
                        String.format("用户名或密码错误，还剩%d次尝试机会", Math.max(0, remaining)));
            }
        }
    }

    // ======================== 登出接口 ========================

    /**
     * 登出：将当前 Token 加入黑名单，使其立即失效
     * 前端调用后清除本地 Token 即可，后端黑名单兜底防止 Token 被重放
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            jwtUtil.blacklistToken(token);
        }
        return Result.success();
    }

    // ======================== 用户信息接口 ========================

    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(org.springframework.security.core.Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        SysUser user = sysUserService.findById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        List<String> roleCodes = sysUserService.getUserRoleCodes(userId);
        List<String> permissionCodes = sysUserService.getUserPermissionCodes(userId);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("departmentId", user.getDepartmentId());
        if (user.getDepartmentId() != null) {
            departmentRepository.findById(user.getDepartmentId())
                    .ifPresent(dept -> {
                        userInfo.put("departmentName", dept.getDeptName());
                        if (dept.getCompanyId() != null) {
                            userInfo.put("companyId", dept.getCompanyId());
                        }
                    });
        }
        userInfo.put("roles", roleCodes);
        userInfo.put("permissions", permissionCodes);
        return Result.success(userInfo);
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED", "REMOTE_ADDR"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
