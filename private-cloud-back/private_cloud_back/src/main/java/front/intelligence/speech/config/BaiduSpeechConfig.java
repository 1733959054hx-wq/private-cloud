package front.intelligence.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 百度语音识别配置
 */
@Configuration
@ConfigurationProperties(prefix = "baidu.speech")
public class BaiduSpeechConfig {

    /** 百度语音应用ID */
    private String appId;

    /** API Key（与OCR共用） */
    private String apiKey;

    /** Secret Key（与OCR共用） */
    private String secretKey;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
}
