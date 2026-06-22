package front.search.qa.config;

import front.intelligence.ai.config.DeepSeekConfig;
import front.intelligence.ai.config.GlmConfig;
import front.intelligence.ai.config.MimoConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 多 AI Provider 路由配置
 * <p>
 * 复用 AI 小助手 / 智能回答页面已有的 {@link GlmConfig}、{@link DeepSeekConfig}、{@link MimoConfig}
 * 配置，避免 application.properties 中重复配置多份 API Key。
 * </p>
 * <p>
 * 模型到 Provider 映射（model -> provider）：
 * <ul>
 *   <li>deepseek-chat / deepseek-v4-flash / deepseek-v4-pro -> deepseek</li>
 *   <li>glm-4.7-flash / glm* -> glm</li>
 *   <li>mimo-v2.5 / mimo-v2.5-pro / mimo* -> mimo</li>
 * </ul>
 * </p>
 */
@Data
@Component
public class AiProviderConfig {

    @Autowired
    private GlmConfig glmConfig;

    @Autowired
    private DeepSeekConfig deepSeekConfig;

    @Autowired
    private MimoConfig mimoConfig;

    /** 默认 Provider（当模型无法识别时使用） */
    private String defaultProvider = "deepseek";

    @Data
    public static class ProviderProps {
        private String apiKey;
        private String baseUrl;
        /** 最终传给 AI API 的真实模型名 */
        private String model;
    }

    /**
     * 根据前端传入的模型名解析出对应的 Provider 配置（含真实模型名）
     * <p>
     * 规则：模型名前缀决定厂商；同厂商下根据是否包含 "pro" 选择专业版或快速版模型。
     * 无法识别时兜底使用 DeepSeek 快速版。
     * </p>
     */
    public ProviderProps resolve(String model) {
        if (model == null || model.isBlank()) {
            return deepSeekProps(false);
        }
        String lower = model.toLowerCase();
        if (lower.startsWith("glm")) {
            return glmProps();
        } else if (lower.startsWith("mimo")) {
            return mimoProps(lower.contains("pro"));
        } else if (lower.startsWith("deepseek")) {
            return deepSeekProps(lower.contains("pro"));
        } else {
            return deepSeekProps(false);
        }
    }

    private ProviderProps glmProps() {
        ProviderProps p = new ProviderProps();
        p.setApiKey(glmConfig.getApiKey());
        p.setBaseUrl(glmConfig.getBaseUrl());
        p.setModel(glmConfig.getFreeModel());
        return p;
    }

    private ProviderProps deepSeekProps(boolean pro) {
        ProviderProps p = new ProviderProps();
        p.setApiKey(deepSeekConfig.getApiKey());
        p.setBaseUrl(deepSeekConfig.getBaseUrl());
        p.setModel(pro ? deepSeekConfig.getProModel() : deepSeekConfig.getFlashModel());
        return p;
    }

    private ProviderProps mimoProps(boolean pro) {
        ProviderProps p = new ProviderProps();
        p.setApiKey(mimoConfig.getApiKey());
        p.setBaseUrl(mimoConfig.getBaseUrl());
        p.setModel(pro ? mimoConfig.getProModel() : mimoConfig.getFlashModel());
        return p;
    }
}
