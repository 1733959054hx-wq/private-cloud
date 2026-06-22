package front.search.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 配置 - 从 application.properties 读取
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiConfig {
    /** AI 提供商：deepseek / qwen */
    private String provider = "deepseek";
    /** API Key */
    private String apiKey = "";
    /** API 地址 */
    private String baseUrl = "https://api.deepseek.com";
    /** 默认模型 */
    private String model = "deepseek-chat";
}
