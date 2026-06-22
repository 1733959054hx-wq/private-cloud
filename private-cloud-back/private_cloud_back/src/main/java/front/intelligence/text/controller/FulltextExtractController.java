package front.intelligence.text.controller;

import front.hxconfig.Result;
import front.intelligence.text.service.FulltextExtractService;
import front.workspace.documentspace.entity.DocFile;
import front.workspace.documentspace.repository.DocFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件全文内容提取 API
 * 自动提取各种文件类型的文字信息并保存到数据库 fulltext_content 字段
 */
@RestController
@RequestMapping("/api/front/text-extract")
public class FulltextExtractController {

    @Autowired
    private FulltextExtractService fulltextExtractService;

    @Autowired
    private DocFileRepository docFileRepository;

    /**
     * 提取单个文件的全文内容并保存到数据库
     * POST /api/front/text-extract/{fileId}
     */
    @PostMapping("/{fileId}")
    public Result<FulltextExtractService.ExtractResult> extractText(@PathVariable Long fileId) {
        try {
            FulltextExtractService.ExtractResult result = fulltextExtractService.extractAndSave(fileId);
            if (result.isSuccess()) {
                return Result.success(result);
            }
            return Result.error(result.getErrorMsg() != null ? result.getErrorMsg() : "文本提取失败");
        } catch (Exception e) {
            return Result.error("文本提取失败: " + e.getMessage());
        }
    }

    /**
     * 提取所有未处理文件的全文内容并保存
     * POST /api/front/text-extract/batch
     */
    @PostMapping("/batch")
    public Result<List<FulltextExtractService.ExtractResult>> extractAllPending() {
        try {
            List<FulltextExtractService.ExtractResult> results = fulltextExtractService.extractAllPending();
            return Result.success(results);
        } catch (Exception e) {
            return Result.error("批量提取失败: " + e.getMessage());
        }
    }

    /**
     * 预览文本提取结果（读取已有内容，不修改数据库）
     * GET /api/front/text-extract/{fileId}
     */
    @GetMapping("/{fileId}")
    public Result<String> getExtractedText(@PathVariable Long fileId) {
        try {
            DocFile docFile = docFileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件不存在"));
            String content = docFile.getFulltextContent();
            if (content == null || content.isEmpty()) {
                content = fulltextExtractService.extractText(docFile);
            }
            return Result.success(content != null ? content : "");
        } catch (Exception e) {
            return Result.error("获取文本失败: " + e.getMessage());
        }
    }

    /**
     * 调试接口：查看文件信息和实际磁盘路径
     * GET /api/front/text-extract/{fileId}/debug
     */
    @GetMapping("/{fileId}/debug")
    public Result<java.util.Map<String, Object>> debugFile(@PathVariable Long fileId) {
        try {
            DocFile docFile = docFileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("文件不存在"));

            java.util.Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("fileId", docFile.getId());
            info.put("fileName", docFile.getFileName());
            info.put("fileType", docFile.getFileType());
            info.put("filePath", docFile.getFilePath());
            info.put("storageType", docFile.getStorageType());
            info.put("hasFulltext", docFile.getFulltextContent() != null && !docFile.getFulltextContent().isEmpty());

            // 解析路径
            String fp = docFile.getFilePath();
            info.put("cwd", System.getProperty("user.dir"));
            info.put("uploadDir", fulltextExtractService.getUploadDir());

            java.util.List<String> triedPaths = new java.util.ArrayList<>();
            if (fp != null) {
                triedPaths.add("直接: " + new java.io.File(fp).getAbsolutePath() + " exists=" + new java.io.File(fp).exists());
                triedPaths.add("user.dir: " + new java.io.File(System.getProperty("user.dir"), fp).getAbsolutePath() + " exists=" + new java.io.File(System.getProperty("user.dir"), fp).exists());
                String clean = fp.replaceAll("^[\\\\/.]*uploads?[\\\\/]", "");
                triedPaths.add("uploadDir: " + new java.io.File(fulltextExtractService.getUploadDir(), clean).getAbsolutePath() + " exists=" + new java.io.File(fulltextExtractService.getUploadDir(), clean).exists());
            }
            info.put("triedPaths", triedPaths);

            return Result.success(info);
        } catch (Exception e) {
            return Result.error("调试失败: " + e.getMessage());
        }
    }
}
