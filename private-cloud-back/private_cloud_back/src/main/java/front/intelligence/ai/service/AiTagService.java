package front.intelligence.ai.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import front.intelligence.ai.entity.DocMetadata;
import front.intelligence.ai.repository.DocMetadataRepository;
import front.intelligence.ai.config.DeepSeekConfig;
import front.intelligence.ai.config.GlmConfig;
import front.intelligence.ai.config.MimoConfig;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AiTagService {

    @Autowired
    @Lazy
    private AiTagService self;

    @Autowired
    private DocMetadataRepository metadataRepository;

    @Autowired
    private DocFileRepository fileRepository;

    @Autowired
    private DocTagService docTagService;

    @Autowired
    private DeepSeekConfig deepSeekConfig;

    @Autowired
    private GlmConfig glmConfig;

    @Autowired
    private MimoConfig mimoConfig;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String SYSTEM_PROMPT = "你是一个文档元数据提取专家。请从文档内容中提取关键元数据，以JSON数组格式返回。" +
            "每个元素包含tag_key(标签键)和tag_value(标签值)。" +
            "常见标签键包括: contract_amount(合同金额), party_a(甲方名称), party_b(乙方名称), " +
            "contract_date(合同日期), document_type(文档类型), subject(主题)。" +
            "只返回JSON数组，不要其他文字。";

    @Async
    public void extractTagsAsync(Long fileId) {
        try {
            DocFile file = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件不存在, fileId=" + fileId));

            String content = file.getFulltextContent();
            if (content == null || content.trim().isEmpty()) {
                return;
            }

            String truncatedContent = content.substring(0, Math.min(content.length(), 8000));
            String tagsJson = null;
            String modelName = null;

            try {
                tagsJson = callGlmForTags(truncatedContent, file.getFileName());
                modelName = "glm-4.7-flash";
            } catch (Exception e1) {
                System.err.println("GLM标签提取失败, 尝试DeepSeek: " + e1.getMessage());
                try {
                    tagsJson = callDeepSeekForTags(truncatedContent, file.getFileName());
                    modelName = "deepseek-v4-flash";
                } catch (Exception e2) {
                    System.err.println("DeepSeek标签提取失败, 尝试MiMo: " + e2.getMessage());
                    try {
                        tagsJson = callMimoForTags(truncatedContent, file.getFileName());
                        modelName = "mimo-v2.5";
                    } catch (Exception e3) {
                        System.err.println("所有AI模型标签提取均失败: " + e3.getMessage());
                        return;
                    }
                }
            }

            if (tagsJson != null) {
                self.saveTags(fileId, tagsJson, modelName);
            }
        } catch (Exception e) {
            System.err.println("AI标签提取失败, fileId=" + fileId + ", error=" + e.getMessage());
        }
    }

    private String callGlmForTags(String content, String fileName) throws IOException {
        return callAiApi(glmConfig.getBaseUrl() + "/chat/completions",
                glmConfig.getApiKey(), glmConfig.getFreeModel(), content, fileName);
    }

    private String callDeepSeekForTags(String content, String fileName) throws IOException {
        return callAiApi(deepSeekConfig.getBaseUrl() + "/v1/chat/completions",
                deepSeekConfig.getApiKey(), deepSeekConfig.getFlashModel(), content, fileName);
    }

    private String callMimoForTags(String content, String fileName) throws IOException {
        return callAiApi(mimoConfig.getBaseUrl() + "/chat/completions",
                mimoConfig.getApiKey(), mimoConfig.getFlashModel(), content, fileName);
    }

    private String callAiApi(String url, String apiKey, String model, String content, String fileName) throws IOException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);
        messages.add(systemMessage);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", "文件名: " + fileName + "\n\n文档内容:\n" + content);
        messages.add(userMessage);

        requestBody.put("messages", messages);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        requestBody.toJSONString(),
                        MediaType.parse("application/json; charset=utf-8")
                ))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = "";
                try {
                    if (response.body() != null) {
                        errorBody = response.body().string();
                    }
                } catch (Exception ignored) {}
                throw new IOException("API返回错误 " + response.code() + ": " + errorBody);
            }
            String responseBody = response.body().string();
            JSONObject jsonResponse = JSONObject.parseObject(responseBody);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("API返回空choices");
            }
            return choices.getJSONObject(0)
                    .getJSONObject("message").getString("content");
        }
    }

    @Transactional
    public void saveTags(Long fileId, String tagsJson, String sourceModel) {
        try {
            System.out.println("AI返回的原始内容: " + tagsJson);
            
            String cleanJson = tagsJson.trim();
            if (cleanJson.startsWith("```")) {
                int start = cleanJson.indexOf("[");
                int end = cleanJson.lastIndexOf("]");
                if (start >= 0 && end > start) {
                    cleanJson = cleanJson.substring(start, end + 1);
                }
            }
            
            System.out.println("清理后的JSON: " + cleanJson);

            JSONArray tagsArray = JSONArray.parseArray(cleanJson);
            if (tagsArray == null || tagsArray.isEmpty()) {
                System.err.println("AI返回的标签JSON为空");
                return;
            }

            for (int i = 0; i < tagsArray.size(); i++) {
                JSONObject tag = tagsArray.getJSONObject(i);
                DocMetadata metadata = new DocMetadata();
                metadata.setFileId(fileId);
                metadata.setTagKey(tag.getString("tag_key"));
                metadata.setTagValue(tag.getString("tag_value"));
                Double confidence = tag.getDouble("confidence");
                metadata.setConfidence(confidence != null ? confidence : 0.8);
                metadata.setSourceModel(sourceModel);
                metadataRepository.save(metadata);
            }
            System.out.println("AI标签已保存到doc_metadata, fileId=" + fileId + ", 标签数=" + tagsArray.size());
        } catch (Exception e) {
            System.err.println("解析AI标签JSON失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Transactional
    public void confirmTags(Long fileId) {
        List<DocMetadata> metadataList = metadataRepository.findByFileId(fileId);
        for (DocMetadata meta : metadataList) {
            // 过滤空值标签
            if (meta.getTagValue() == null || meta.getTagValue().trim().isEmpty()) {
                continue;
            }
            String tagName = meta.getTagKey() + ": " + meta.getTagValue().trim();
            BigDecimal conf = meta.getConfidence() != null
                    ? BigDecimal.valueOf(meta.getConfidence())
                    : new BigDecimal("0.8");
            docTagService.addAiTag(fileId, tagName, conf);
        }
        metadataRepository.deleteByFileId(fileId);
    }

    @Transactional
    public void dismissTags(Long fileId) {
        metadataRepository.deleteByFileId(fileId);
    }

    public List<DocMetadata> getFileTags(Long fileId) {
        return metadataRepository.findByFileId(fileId);
    }

    @Transactional
    public void reExtractTags(Long fileId) {
        metadataRepository.deleteByFileId(fileId);
        docTagService.removeFileTags(fileId);
        extractTagsAsync(fileId);
    }
}