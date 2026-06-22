package front.intelligence.speech.controller;

import front.hxconfig.Result;
import front.intelligence.speech.service.BaiduSpeechService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音识别控制器
 * 接收前端录制的音频，返回识别文字
 */
@RestController
@RequestMapping("/api/front/speech")
public class SpeechController {

    @Autowired
    private BaiduSpeechService speechService;

    /**
     * 上传音频并识别文字
     * POST /api/front/speech/recognize
     *
     * @param file   音频文件（前端录制）
     * @param format 音频格式（pcm / wav / webm），默认 wav
     * @param rate   采样率，默认 16000
     */
    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "wav") String format,
            @RequestParam(defaultValue = "16000") int rate) {

        if (file.isEmpty()) {
            return Result.error(400, "音频文件为空");
        }

        try {
            byte[] audioData = file.getBytes();
            String text = speechService.recognize(audioData, format, rate);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", text);
            data.put("format", format);
            data.put("rate", rate);
            data.put("size", audioData.length);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(500, "语音识别失败: " + e.getMessage());
        }
    }
}
