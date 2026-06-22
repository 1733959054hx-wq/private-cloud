package front.hxconfig;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * IP 网络准入拦截器（NAC - Network Access Control）
 * - 模拟企业级网络准入控制：必须连接指定物理网络才能访问系统
 * - 支持多个准入网段（逗号分隔），如校园网有线 + 红米手机热点
 * - 非准入IP返回 403 JSON 响应
 * - 可通过 app.ip-filter.enabled 配置开关
 * - 准入网段通过 app.ip-filter.allowed-subnets 配置（逗号分隔多个网段前缀）
 */
@Component
public class IpAccessFilter extends OncePerRequestFilter {

    @Value("${app.ip-filter.enabled:false}")
    private boolean ipFilterEnabled;

    /** 准入网段前缀列表（逗号分隔），如 "192.168.10.,10.50.167." */
    @Value("${app.ip-filter.allowed-subnets:192.168.10.,10.50.167.}")
    private String allowedSubnetsConfig;

    private List<String> getAllowedSubnets() {
        return List.of(allowedSubnetsConfig.split(","));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 未启用则直接放行
        if (!ipFilterEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // WebSocket路径跳过IP检查（有独立的STOMP鉴权）
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        if (isAllowedIp(clientIp)) {
            filterChain.doFilter(request, response);
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("<!DOCTYPE html><html><head><title>404</title></head><body><h1>404 Not Found</h1></body></html>");
        }
    }

    /**
     * 获取客户端真实IP（支持反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        // IPv6本地回环映射为IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }

    /**
     * 判断是否为准入网段IP
     * 遍历所有配置的准入网段前缀，任一匹配即放行
     * 不允许 localhost（强制通过真实网络IP访问）
     */
    private boolean isAllowedIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // 遍历所有准入网段前缀，任一匹配即放行
        // 例如 "192.168.10." 放行校园网有线，"10.50.167." 放行红米热点
        for (String subnet : getAllowedSubnets()) {
            String trimmed = subnet.trim();
            if (!trimmed.isEmpty() && ip.startsWith(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
