package front.intelligence.ai.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import front.intelligence.ai.config.DeepSeekConfig;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DeepSeekService {

    @Autowired
    private DeepSeekConfig deepSeekConfig;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public String summarizeDocument(String documentContent, String modelType) throws IOException {
        String model = "pro".equalsIgnoreCase(modelType)
                ? deepSeekConfig.getProModel()
                : deepSeekConfig.getFlashModel();

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个专业的文档助手，擅长总结和分析文档内容。请用简洁清晰的语言回答问题。输出格式要求：1. 使用标准Markdown格式；2. 表格必须符合GFM规范，表头行、分隔行（如|:---|）、数据行必须各占一行，中间不能有空行；3. 代码块使用```语言名包裹。");
        messages.add(systemMessage);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", "请总结以下文档内容：\n\n" + documentContent);
        messages.add(userMessage);

        requestBody.put("messages", messages);

        Request request = new Request.Builder()
                .url(deepSeekConfig.getBaseUrl() + "/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        requestBody.toJSONString(),
                        MediaType.parse("application/json; charset=utf-8")
                ))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DeepSeek API调用失败: " + response.code() + " - " + response.message());
            }
            String responseBody = response.body().string();
            JSONObject jsonResponse = JSONObject.parseObject(responseBody);
            return jsonResponse.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
        }
    }

    public String chat(String userMessage, String modelType) throws IOException {
        String model = "pro".equalsIgnoreCase(modelType)
                ? deepSeekConfig.getProModel()
                : deepSeekConfig.getFlashModel();

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", userMessage);
        messages.add(message);

        requestBody.put("messages", messages);

        Request request = new Request.Builder()
                .url(deepSeekConfig.getBaseUrl() + "/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        requestBody.toJSONString(),
                        MediaType.parse("application/json; charset=utf-8")
                ))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DeepSeek API调用失败: " + response.code() + " - " + response.message());
            }
            String responseBody = response.body().string();
            JSONObject jsonResponse = JSONObject.parseObject(responseBody);
            return jsonResponse.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
        }
    }

    public SseEmitter chatStream(List<Map<String, String>> historyMessages, String modelType) {
        SseEmitter emitter = new SseEmitter(300000L);
        String model = "pro".equalsIgnoreCase(modelType)
                ? deepSeekConfig.getProModel()
                : deepSeekConfig.getFlashModel();

        new Thread(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", model);
                requestBody.put("stream", true);
                // 取消token限制，避免复杂回答被截断

                JSONArray messages = new JSONArray();
                for (Map<String, String> msg : historyMessages) {
                    JSONObject m = new JSONObject();
                    m.put("role", msg.get("role"));
                    m.put("content", msg.get("content"));
                    messages.add(m);
                }
                requestBody.put("messages", messages);

                Request request = new Request.Builder()
                        .url(deepSeekConfig.getBaseUrl() + "/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(
                                requestBody.toJSONString(),
                                MediaType.parse("application/json; charset=utf-8")
                        ))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        emitter.send(SseEmitter.event().name("error")
                                .data("DeepSeek API调用失败: " + response.code()));
                        emitter.complete();
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) break;
                            try {
                                JSONObject json = JSONObject.parseObject(data);
                                JSONArray choices = json.getJSONArray("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                    if (delta != null && delta.containsKey("content")) {
                                        String content = delta.getString("content");
                                        if (content != null) {
                                            emitter.send(SseEmitter.event().name("content").data(content));
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    emitter.send(SseEmitter.event().name("done").data("complete"));
                    emitter.complete();
                }
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("流式调用失败: " + e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}
