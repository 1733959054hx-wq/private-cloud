package front.intelligence.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mimo")
public class MimoConfig {
    private String apiKey;
    private String baseUrl = "https://token-plan-cn.xiaomimimo.com/v1";
    private String flashModel = "mimo-v2.5";
    private String proModel = "mimo-v2.5-pro";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getFlashModel() { return flashModel; }
    public void setFlashModel(String flashModel) { this.flashModel = flashModel; }
    public String getProModel() { return proModel; }
    public void setProModel(String proModel) { this.proModel = proModel; }
}
