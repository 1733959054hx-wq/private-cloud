package front.intelligence.ai.service;

import front.intelligence.ai.entity.DocTag;
import front.intelligence.ai.repository.DocTagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DocTagService {

    @Autowired
    private DocTagRepository docTagRepository;

    public List<DocTag> getFileTags(Long fileId) {
        return docTagRepository.findByFileId(fileId);
    }

    public List<DocTag> getTagsByName(String tagName) {
        return docTagRepository.findByTagName(tagName);
    }

    /** 获取标签云数据：每个标签及其出现次数 */
    public List<Map<String, Object>> getTagCloud() {
        List<Object[]> rows = docTagRepository.findTagCloud();
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Object[] row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("tagName", row[0]);
                item.put("count", row[1]);
                result.add(item);
            }
        }
        return result;
    }

    /** 按标签名获取关联的文件ID列表 */
    public List<Long> getFileIdsByTagName(String tagName) {
        return docTagRepository.findFileIdsByTagName(tagName);
    }

    @Transactional
    public DocTag addManualTag(Long fileId, String tagName) {
        DocTag tag = new DocTag();
        tag.setFileId(fileId);
        tag.setTagName(tagName);
        tag.setTagSource("manual");
        tag.setConfidence(new BigDecimal("1.00"));
        return docTagRepository.save(tag);
    }

    @Transactional
    public DocTag updateTagName(Long tagId, String tagName) {
        DocTag tag = docTagRepository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("标签不存在, id=" + tagId));
        tag.setTagName(tagName);
        return docTagRepository.save(tag);
    }

    @Transactional
    public DocTag addAiTag(Long fileId, String tagName, BigDecimal confidence) {
        DocTag tag = new DocTag();
        tag.setFileId(fileId);
        tag.setTagName(tagName);
        tag.setTagSource("AI");
        tag.setConfidence(confidence);
        return docTagRepository.save(tag);
    }

    @Transactional
    public void removeTag(Long tagId) {
        docTagRepository.deleteById(tagId);
    }

    @Transactional
    public void removeFileTags(Long fileId) {
        docTagRepository.deleteByFileId(fileId);
    }
}
