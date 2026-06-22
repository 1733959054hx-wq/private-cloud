package front.search.engine.es;

import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ES 索引同步服务：将 MySQL 数据同步到 ES
 */
@Service
public class DocFileIndexService {

    @Autowired(required = false)
    private DocFileSearchRepository searchRepository;

    @Resource
    private DocFileRepository docFileRepository;

    @PostConstruct
    public void init() {
        try {
            rebuildIndex();
            System.out.println("===== ES 索引重建完成 =====");
        } catch (Exception e) {
            System.err.println("===== ES 索引重建失败 =====");
            e.printStackTrace();
        }
    }

    public void rebuildIndex() {
        searchRepository.deleteAll();

        List<DocFile> docs = docFileRepository.findByDeletedAndStatus(0, 1);
        List<DocFileDocument> esDocs = docs.stream()
                .map(this::convertToEsDoc)
                .collect(Collectors.toList());

        // 分批保存，单条文档截断至5MB，批次限制10条保底
        int batchSize = 10;
        int total = esDocs.size();
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            searchRepository.saveAll(esDocs.subList(i, end));
            System.out.println("===== ES 索引进度: " + end + "/" + total + " =====");
        }
    }

    public void syncDocument(DocFile doc) {
        if (doc == null || (doc.getDeleted() != null && doc.getDeleted() == 1)) {
            return;
        }
        searchRepository.save(convertToEsDoc(doc));
    }

    public void deleteDocument(Long docId) {
        searchRepository.deleteById(docId);
    }

    /** ES 索引文本最大长度，搜索匹配前5000字足够，AI问答走 MySQL 读全文 */
    private static final int MAX_CONTENT_LENGTH = 5000;

    private DocFileDocument convertToEsDoc(DocFile doc) {
        DocFileDocument esDoc = new DocFileDocument();
        esDoc.setId(doc.getId());
        esDoc.setFileName(doc.getFileName());
        String content = doc.getFulltextContent();
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        esDoc.setFulltextContent(content);
        esDoc.setUploaderName(doc.getUploaderName());
        esDoc.setFileType(doc.getFileType());
        esDoc.setFileSize(doc.getFileSize());
        esDoc.setDepartmentId(doc.getDepartmentId());
        esDoc.setSpaceType(doc.getSpaceType());
        esDoc.setSpaceId(doc.getSpaceId());
        esDoc.setUploaderId(doc.getUploaderId());
        if (doc.getCreateTime() != null) {
            esDoc.setCreateTime(java.sql.Timestamp.valueOf(doc.getCreateTime()));
        }
        return esDoc;
    }
}
