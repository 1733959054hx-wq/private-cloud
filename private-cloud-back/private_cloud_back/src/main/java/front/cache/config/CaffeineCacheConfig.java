package front.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置（L1 缓存）
 *
 * 缓存名称与规格通过 application.properties 配置：
 *   cache.caffeine.names=userPermissions,fileList,sysConfig
 *   cache.caffeine.user-permissions.spec=maximumSize=1000,expireAfterWrite=10m
 *   cache.caffeine.file-list.spec=maximumSize=500,expireAfterWrite=30s
 *   cache.caffeine.sys-config.spec=maximumSize=200,expireAfterWrite=30m
 *
 * 规格语法遵循 Caffeine spec：
 *   maximumSize=N,expireAfterWrite=Tm,expireAfterAccess=Tm,recordStats
 */
@Configuration
@ConditionalOnClass(Caffeine.class)
public class CaffeineCacheConfig {

    @Value("${cache.caffeine.names:}")
    private String cacheNames;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        List<org.springframework.cache.Cache> caches = new ArrayList<>();

        if (StringUtils.hasText(cacheNames)) {
            for (String name : cacheNames.split(",")) {
                String trimmed = name.trim();
                if (trimmed.isEmpty()) continue;
                Caffeine<Object, Object> builder = parseSpec(trimmed);
                caches.add(new CaffeineCache(trimmed, builder.build()));
            }
        }
        manager.setCaches(caches);
        return manager;
    }

    /**
     * 解析规格配置
     * 配置键规则：将缓存名 camelCase 转 kebab-case
     *   如 fileList -> cache.caffeine.file-list.spec
     */
    private Caffeine<Object, Object> parseSpec(String cacheName) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        String propKey = "cache.caffeine." + camelToKebab(cacheName) + ".spec";
        // 通过 System.getProperty 或 Spring Environment 读取（这里简化用 System 读取占位符解析后的值）
        // 实际通过 @Value 注入更稳妥，这里采用一个内部 Map
        String spec = specs.get(cacheName);
        if (spec == null || spec.isEmpty()) {
            // 默认规格
            builder.maximumSize(500).expireAfterWrite(5, TimeUnit.MINUTES);
            return builder;
        }
        applySpec(builder, spec);
        return builder;
    }

    /**
     * 应用规格字符串到 builder
     */
    private void applySpec(Caffeine<Object, Object> builder, String spec) {
        for (String part : spec.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            String value = kv[1].trim();
            switch (key) {
                case "maximumSize" -> builder.maximumSize(Long.parseLong(value));
                case "expireAfterWrite" -> builder.expireAfterWrite(parseDuration(value), TimeUnit.MILLISECONDS);
                case "expireAfterAccess" -> builder.expireAfterAccess(parseDuration(value), TimeUnit.MILLISECONDS);
                case "recordStats" -> { if (Boolean.parseBoolean(value)) builder.recordStats(); }
                default -> { /* 忽略未知 key */ }
            }
        }
    }

    /**
     * 解析时间字符串：10m / 30s / 1h
     */
    private long parseDuration(String value) {
        if (value.endsWith("ms")) return Long.parseLong(value.substring(0, value.length() - 2));
        if (value.endsWith("s")) return Long.parseLong(value.substring(0, value.length() - 1)) * 1000;
        if (value.endsWith("m")) return Long.parseLong(value.substring(0, value.length() - 1)) * 60 * 1000;
        if (value.endsWith("h")) return Long.parseLong(value.substring(0, value.length() - 1)) * 3600 * 1000;
        return Long.parseLong(value);
    }

    private String camelToKebab(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * 通过 @ConfigurationProperties 风格注入规格
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setSpecs(
            @Value("${cache.caffeine.user-permissions.spec:}") String userPermissions,
            @Value("${cache.caffeine.file-list.spec:}") String fileList,
            @Value("${cache.caffeine.sys-config.spec:}") String sysConfig) {
        if (StringUtils.hasText(userPermissions)) specs.put("userPermissions", userPermissions);
        if (StringUtils.hasText(fileList)) specs.put("fileList", fileList);
        if (StringUtils.hasText(sysConfig)) specs.put("sysConfig", sysConfig);
    }

    private final Map<String, String> specs = new HashMap<>();
}
