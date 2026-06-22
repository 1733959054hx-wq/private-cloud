package front.intelligence.speech.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import front.intelligence.speech.config.BaiduSpeechConfig;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 百度语音识别服务
 * 调用百度短语音识别 API，将音频转为文字
 *
 * API 文档：https://ai.baidu.com/ai-doc/SPEECH/Vk38lxily
 * 免费额度：每月 2 万次调用
 */
@Service
public class BaiduSpeechService {

    @Autowired
    private BaiduSpeechConfig config;

    private final OkHttpClient client = new OkHttpClient();
    private String cachedAccessToken;
    private long tokenExpireTime;

    /** 获取百度 API access_token（有效期约30天，缓存复用） */
    private synchronized String getAccessToken() throws IOException {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return cachedAccessToken;
        }

        String url = "https://aip.baidubce.com/oauth/2.0/token"
                + "?grant_type=client_credentials"
                + "&client_id=" + config.getApiKey()
                + "&client_secret=" + config.getSecretKey();

        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("获取百度 token 失败: " + resp.code());
            }
            JSONObject json = JSON.parseObject(resp.body().string());
            cachedAccessToken = json.getString("access_token");
            // 提前 1 天过期
            int expiresIn = json.getIntValue("expires_in");
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 86400) * 1000L;
            return cachedAccessToken;
        }
    }

    /**
     * 短语音识别（识别一段不超过 60 秒的语音）
     *
     * @param audioData 音频字节（PCM/WAV/AMR，16000Hz 采样率）
     * @param format    音频格式：pcm / wav / amr / m4a
     * @param rate      采样率，默认 16000
     * @return 识别出的文字
     */
    public String recognize(byte[] audioData, String format, int rate) throws IOException {
        String token = getAccessToken();
        System.out.println("[语音识别] token前10位=" + token.substring(0, 10));
        System.out.println("[语音识别] 音频数据前50字节(hex)=" + bytesToHex(audioData, 50));

        // 先试 wav 不带 rate（从文件头自动读取）
        String url1 = "https://vop.baidu.com/server_api?cuid=pc&token=" + token;
        JSONObject p1 = new JSONObject();
        p1.put("format", "wav");
        p1.put("channel", 1);
        p1.put("cuid", "pc");
        p1.put("speech", java.util.Base64.getEncoder().encodeToString(audioData));
        p1.put("len", audioData.length);

        System.out.println("[语音识别] 方案1(wav无rate): dev_pid=无, format=wav, rate=无");
        try (Response r = client.newCall(new Request.Builder().url(url1)
                .post(RequestBody.create(MediaType.parse("application/json"), p1.toJSONString()))
                .addHeader("Content-Type", "application/json").build()).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            System.out.println("[语音识别] 方案1返回: " + s);
            if (!s.contains("\"err_no\":33")) return parseResult(s);
        }

        // 再试 wav + rate=16000
        String url2 = "https://vop.baidu.com/server_api?cuid=pc&token=" + token;
        JSONObject p2 = new JSONObject(p1);
        p2.put("rate", 16000);
        System.out.println("[语音识别] 方案2(wav+rate16000)");
        try (Response r = client.newCall(new Request.Builder().url(url2)
                .post(RequestBody.create(MediaType.parse("application/json"), p2.toJSONString()))
                .addHeader("Content-Type", "application/json").build()).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            System.out.println("[语音识别] 方案2返回: " + s);
            if (!s.contains("\"err_no\":33")) return parseResult(s);
        }

        // 试 pcm + rate=16000（去掉WAV头）
        String url3 = "https://vop.baidu.com/server_api?cuid=pc&token=" + token;
        JSONObject p3 = new JSONObject();
        p3.put("format", "pcm");
        p3.put("rate", 16000);
        p3.put("channel", 1);
        p3.put("cuid", "pc");
        byte[] raw = audioData.length > 44 ? java.util.Arrays.copyOfRange(audioData, 44, audioData.length) : audioData;
        p3.put("speech", java.util.Base64.getEncoder().encodeToString(raw));
        p3.put("len", raw.length);

        System.out.println("[语音识别] 方案3(pcm+rate16000)");
        try (Response r = client.newCall(new Request.Builder().url(url3)
                .post(RequestBody.create(MediaType.parse("application/json"), p3.toJSONString()))
                .addHeader("Content-Type", "application/json").build()).execute()) {
            String s = r.body() != null ? r.body().string() : "";
            System.out.println("[语音识别] 方案3返回: " + s);
            if (!s.contains("\"err_no\":33")) return parseResult(s);
        }

        throw new IOException("所有方案均失败，请检查百度云应用是否开通语音识别服务");
    }

    private String parseResult(String respStr) {
        JSONObject r = JSON.parseObject(respStr);
        int errNo = r.getIntValue("err_no");
        if (errNo != 0) throw new RuntimeException(r.getString("err_msg"));
        return r.getJSONArray("result").getString(0);
    }

    private String bytesToHex(byte[] data, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, max); i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        return sb.toString();
    }
}
