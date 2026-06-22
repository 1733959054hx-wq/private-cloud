package front.hxconfig;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 认证工具类 - 统一处理 Authentication 的空值检查和用户ID提取
 */
public class AuthUtil {

    /**
     * 检查认证是否有效（非空、已认证、非匿名）
     */
    public static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * 从认证中提取用户ID，如果认证无效返回 null
     */
    public static Long getUserId(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}
