package front.hxconfig;

import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private HxJwtFilter hxJwtFilter;

    @Autowired
    private IpAccessFilter ipAccessFilter;

    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (securityEnabled) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/front/auth/**").permitAll()
                            .requestMatchers("/ws/**").permitAll()
                            .requestMatchers("/api/front/ai/chat/stream").permitAll()
                            .requestMatchers("/api/front/ai/chat/rag/stream").permitAll()
                            .requestMatchers("/api/front/ai/chat/file/stream").permitAll()
                            .requestMatchers("/api/front/ai/generate/stream/**").permitAll()
                            .requestMatchers("/api/front/ai/history/**").permitAll()
                            .requestMatchers("/api/front/ai/prompts/**").permitAll()
                            .requestMatchers("/api/front/ai/tags/**").permitAll()
                            .requestMatchers("/api/front/ai/generated-docs/**").permitAll()
                            .requestMatchers("/api/front/preview/**").permitAll()
                            .requestMatchers("/api/front/share-links/token/**").permitAll()
                            .requestMatchers("/api/front/share-links/access/**").permitAll()
                            .requestMatchers("/api/front/share-links/download/**").permitAll()
                            .requestMatchers("/api/admin/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    // 所有自定义 Filter 都以内置 Filter 作为参考点（Spring Security 7 要求）
                    // Filter执行顺序：ipAccessFilter → hxJwtFilter → UsernamePasswordAuthenticationFilter
                    .addFilterBefore(hxJwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(ipAccessFilter, LogoutFilter.class)
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((request, response, authException) -> {
                                response.setContentType("application/json;charset=UTF-8");
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                JSONObject result = new JSONObject();
                                result.put("code", 401);
                                result.put("message", "未登录或Token已过期");
                                result.put("data", null);
                                response.getWriter().write(result.toJSONString());
                            })
                    );
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    // 开发模式也注册所有拦截器（IP拦截器由内部开关控制是否生效）
                    // Filter执行顺序：DevAnonymousFilter → ipAccessFilter → hxJwtFilter
                    .addFilterBefore(hxJwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(ipAccessFilter, LogoutFilter.class)
                    .addFilterBefore(new DevAnonymousFilter(), SecurityContextHolderFilter.class);
        }

        return http.build();
    }
}

class DevAnonymousFilter extends org.springframework.web.filter.OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new AnonymousAuthenticationToken("dev-anonymous", 2L,
                            List.of(new SimpleGrantedAuthority("ROLE_user"))));
        }
        filterChain.doFilter(request, response);
    }
}
