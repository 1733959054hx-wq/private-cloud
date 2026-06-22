package front.intelligence.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {
    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String flashModel = "deepseek-v4-flash";
    private String proModel = "deepseek-v4-pro";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getFlashModel() { return flashModel; }
    public void setFlashModel(String flashModel) { this.flashModel = flashModel; }
    public String getProModel() { return proModel; }
    public void setProModel(String proModel) { this.proModel = proModel; }
}
