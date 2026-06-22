package front.intelligence.signature.service;

import front.intelligence.signature.entity.SignatureRecord;
import front.intelligence.signature.repository.SignatureRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SignatureRecordService {

    @Autowired
    private SignatureRecordRepository signatureRecordRepository;

    @Transactional
    public SignatureRecord signDocument(Long documentId, Long signerId, String signerName,
                                         String sealImageUrl) {
        if (signatureRecordRepository.existsByDocumentIdAndSignerId(documentId, signerId)) {
            throw new RuntimeException("您已签署过此文档");
        }

        SignatureRecord record = new SignatureRecord();
        record.setDocumentId(documentId);
        record.setSignerId(signerId);
        record.setSignerName(signerName);
        record.setSignTime(LocalDateTime.now());
        record.setSealImageUrl(sealImageUrl);
        record.setSignatureHash(generateHash(documentId, signerId, signerName));
        return signatureRecordRepository.save(record);
    }

    public List<SignatureRecord> getDocumentSignatures(Long documentId) {
        return signatureRecordRepository.findByDocumentIdOrderBySignTimeDesc(documentId);
    }

    public List<SignatureRecord> getUserSignatures(Long signerId) {
        return signatureRecordRepository.findBySignerIdOrderBySignTimeDesc(signerId);
    }

    public boolean verifySignature(Long documentId, String signatureHash) {
        List<SignatureRecord> records = signatureRecordRepository.findByDocumentIdOrderBySignTimeDesc(documentId);
        return records.stream().anyMatch(r -> r.getSignatureHash().equals(signatureHash));
    }

    private String generateHash(Long documentId, Long signerId, String signerName) {
        try {
            String data = documentId + ":" + signerId + ":" + signerName + ":" + System.currentTimeMillis();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成签章哈希失败", e);
        }
    }
}
