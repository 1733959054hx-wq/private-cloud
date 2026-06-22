package front.intelligence.signature.controller;

import front.hxconfig.AuthUtil;
import front.hxconfig.Result;
import front.intelligence.signature.entity.SignatureRecord;
import front.intelligence.signature.service.SignatureRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/signatures")
public class SignatureRecordController {

    @Autowired
    private SignatureRecordService signatureRecordService;

    @PostMapping
    public Result<SignatureRecord> signDocument(Authentication authentication,
                                                  @RequestBody Map<String, Object> body) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        Long documentId = Long.valueOf(body.get("documentId").toString());
        String signerName = body.get("signerName").toString();
        String sealImageUrl = body.get("sealImageUrl") != null
                ? body.get("sealImageUrl").toString() : null;

        SignatureRecord record = signatureRecordService.signDocument(
                documentId, userId, signerName, sealImageUrl);
        return Result.success(record);
    }

    @GetMapping("/document/{documentId}")
    public Result<List<SignatureRecord>> getDocumentSignatures(@PathVariable Long documentId) {
        List<SignatureRecord> records = signatureRecordService.getDocumentSignatures(documentId);
        return Result.success(records);
    }

    @GetMapping("/mine")
    public Result<List<SignatureRecord>> getMySignatures(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        if (userId == null) return Result.error(401, "未登录或登录已过期");
        List<SignatureRecord> records = signatureRecordService.getUserSignatures(userId);
        return Result.success(records);
    }

    @PostMapping("/verify")
    public Result<Map<String, Object>> verifySignature(@RequestBody Map<String, Object> body) {
        Long documentId = Long.valueOf(body.get("documentId").toString());
        String signatureHash = body.get("signatureHash").toString();
        boolean valid = signatureRecordService.verifySignature(documentId, signatureHash);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("documentId", documentId);
        result.put("valid", valid);
        return Result.success(result);
    }
}
