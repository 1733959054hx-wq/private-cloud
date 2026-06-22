package front.intelligence.ocr.controller;

import front.hxconfig.Result;
import front.intelligence.ocr.entity.OcrRecord;
import front.intelligence.ocr.service.OcrService;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/front/ocr")
public class OcrController {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private DocFileRepository fileRepository;

    @PostMapping("/{fileId}")
    public Result<Map<String, Object>> triggerOcr(@PathVariable Long fileId,
                                                    @RequestParam(required = false) Integer page) {
        DocFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        OcrRecord record = ocrService.prepareOcrRecord(fileId, page);

        ocrService.triggerOcrAsync(fileId, page);

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        result.put("status", "PROCESSING");
        result.put("message", "OCR任务已提交，正在异步处理");
        if (page != null) {
            result.put("page", page);
        }
        return Result.success(result);
    }

    @GetMapping("/{fileId}/result")
    public Result<Map<String, Object>> getOcrResult(@PathVariable Long fileId,
                                                      @RequestParam(required = false) Integer page) {
        OcrRecord record;
        if (page != null) {
            record = ocrService.getOcrRecordByPage(fileId, page);
        } else {
            record = ocrService.getOcrRecord(fileId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileId);
        if (page != null) {
            result.put("page", page);
        }
        if (record != null) {
            result.put("status", mapStatus(record.getStatus()));
            result.put("ocrText", record.getOcrText());
            result.put("errorMessage", record.getErrorMessage());
            result.put("hasText", record.getOcrText() != null && !record.getOcrText().isEmpty());
        } else {
            result.put("status", "NOT_STARTED");
            result.put("ocrText", null);
            result.put("hasText", false);
        }
        return Result.success(result);
    }

    private String mapStatus(Integer status) {
        return switch (status) {
            case 0 -> "PENDING";
            case 1 -> "PROCESSING";
            case 2 -> "COMPLETED";
            case 3 -> "FAILED";
            default -> "UNKNOWN";
        };
    }
}
