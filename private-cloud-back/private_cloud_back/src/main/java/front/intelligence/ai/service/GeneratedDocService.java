package front.intelligence.ai.service;

import front.intelligence.ai.entity.GeneratedDoc;
import front.intelligence.ai.repository.GeneratedDocRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeneratedDocService {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    @Autowired
    private GeneratedDocRepository generatedDocRepository;

    @Transactional
    public GeneratedDoc saveGeneratedDoc(String fileName, String templateId, String templateName,
                                          String filePath, String content, String model,
                                          Long departmentId, Long creatorId, String creatorName) {
        return saveGeneratedDoc(fileName, templateId, templateName, filePath, content, model, departmentId, creatorId, creatorName, 1);
    }

    @Transactional
    public GeneratedDoc saveGeneratedDoc(String fileName, String templateId, String templateName,
                                          String filePath, String content, String model,
                                          Long departmentId, Long creatorId, String creatorName, int status) {
        GeneratedDoc doc = new GeneratedDoc();
        doc.setFileName(fileName);
        doc.setTemplateId(templateId);
        doc.setTemplateName(templateName);
        doc.setFilePath(filePath);
        doc.setContent(content);
        doc.setModel(model);
        doc.setDepartmentId(departmentId);
        doc.setCreatorId(creatorId);
        doc.setCreatorName(creatorName);
        doc.setStatus(status);
        // 自动从内容中提取 title 作为显示名
        if (content != null) {
            String extractedTitle = extractTitleFromContent(content);
            if (extractedTitle != null) doc.setTitle(extractedTitle);
        }
        return generatedDocRepository.save(doc);
    }

    private static final int MAX_DOCS_RETURN = 100;

    public List<GeneratedDoc> getDocsByDepartment(Long departmentId) {
        List<Integer> activeStatuses = List.of(0, 1, 2);
        List<GeneratedDoc> docs;
        if (departmentId != null) {
            docs = generatedDocRepository.findByDepartmentIdAndStatusInOrderByCreateTimeDesc(departmentId, activeStatuses);
        } else {
            docs = generatedDocRepository.findByStatusInOrderByCreateTimeDesc(activeStatuses);
        }
        return docs.size() > MAX_DOCS_RETURN ? docs.subList(0, MAX_DOCS_RETURN) : docs;
    }

    public List<GeneratedDoc> getDocsByCreator(Long creatorId) {
        List<GeneratedDoc> docs = generatedDocRepository.findByCreatorIdAndStatusInOrderByCreateTimeDesc(creatorId, List.of(0, 1, 2));
        return docs.size() > MAX_DOCS_RETURN ? docs.subList(0, MAX_DOCS_RETURN) : docs;
    }

    public GeneratedDoc getDocById(Long id) {
        return generatedDocRepository.findById(id).orElse(null);
    }

    @Transactional
    public void deleteDoc(Long id) {
        generatedDocRepository.findById(id).ifPresent(doc -> {
            doc.setStatus(-1);
            generatedDocRepository.save(doc);
        });
    }

    @Transactional
    public void updateDocStatus(Long id, int status) {
        updateDocStatus(id, status, null);
    }

    @Transactional
    public void updateDocStatus(Long id, int status, String failReason) {
        generatedDocRepository.findById(id).ifPresent(doc -> {
            doc.setStatus(status);
            if (failReason != null) doc.setFailReason(failReason);
            generatedDocRepository.save(doc);
        });
    }

    @Transactional
    public void updateDocContentAndStatus(Long id, int status, String content, String filePath) {
        generatedDocRepository.findById(id).ifPresent(doc -> {
            doc.setStatus(status);
            doc.setContent(content);
            doc.setFilePath(filePath);
            // 从生成的 Markdown 内容中提取标题存入 title 字段（fileName 保持不变）
            String extractedTitle = extractTitleFromContent(content);
            if (extractedTitle != null && !extractedTitle.isBlank()) {
                doc.setTitle(extractedTitle);
            }
            generatedDocRepository.save(doc);
        });
    }

    /**
     * 从 Markdown 内容中提取第一个标题（# xxx）作为文档名
     */
    private String extractTitleFromContent(String content) {
        if (content == null || content.isBlank()) return null;
        Matcher m = TITLE_PATTERN.matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        // 兑底：取第一行非空文本，截取前50字符
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;
            }
        }
        return null;
    }

    /**
     * 自动将超过1小时仍处于“生成中”的文档标记为失败，
     * 同时为缺少 title 的旧文档从 content 中回填 title。
     * 使用定时任务每10分钟执行一次，避免每次查询都扫描。
     */
    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void autoFailStaleGeneratingDocs() {
        // 1. 标记超时生成中为失败
        List<GeneratedDoc> generatingDocs = generatedDocRepository.findByStatusInOrderByCreateTimeDesc(List.of(0));
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        for (GeneratedDoc doc : generatingDocs) {
            if (doc.getCreateTime() != null && doc.getCreateTime().isBefore(threshold)) {
                doc.setStatus(2);
                generatedDocRepository.save(doc);
            }
        }
        // 2. 为缺少 title 的已完成文档从 content 中回填 title
        List<GeneratedDoc> completedDocs = generatedDocRepository.findByStatusInOrderByCreateTimeDesc(List.of(1));
        for (GeneratedDoc doc : completedDocs) {
            if ((doc.getTitle() == null || doc.getTitle().isBlank()) && doc.getContent() != null) {
                String extractedTitle = extractTitleFromContent(doc.getContent());
                if (extractedTitle != null) {
                    doc.setTitle(extractedTitle);
                    generatedDocRepository.save(doc);
                }
            }
        }
    }
}
