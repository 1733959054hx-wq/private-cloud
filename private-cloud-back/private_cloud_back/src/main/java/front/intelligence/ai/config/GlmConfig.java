package front.intelligence.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "glm")
public class GlmConfig {
    private String apiKey;
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String freeModel = "glm-4.7-flash";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getFreeModel() { return freeModel; }
    public void setFreeModel(String freeModel) { this.freeModel = freeModel; }
}
