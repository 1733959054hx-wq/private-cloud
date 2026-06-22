package front.search.engine.controller;

import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * 文档列表（供协同编辑器选文挡用）
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentListController {

    @Resource
    private DocFileRepository docFileRepository;

    @GetMapping("/list")
    public Map<String, Object> listDocuments() {
        List<Map<String, Object>> docs = docFileRepository.listAllDocuments();

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", docs);
        return result;
    }
}
