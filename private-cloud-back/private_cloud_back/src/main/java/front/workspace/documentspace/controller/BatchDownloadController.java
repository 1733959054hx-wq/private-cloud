package front.workspace.documentspace.controller;

import front.hxconfig.Result;
import front.workspace.documentspace.service.BatchDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/front/files/batch-download")
public class BatchDownloadController {

    @Autowired
    private BatchDownloadService batchDownloadService;

    @PostMapping
    public Result<Map<String, Object>> createBatchDownload(@RequestBody List<Long> fileIds) throws Exception {
        Map<String, Object> result = batchDownloadService.createBatchDownload(fileIds);
        return Result.success(result);
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> downloadBatchFile(@PathVariable String fileName) {
        File file = batchDownloadService.getBatchDownloadFile(fileName);
        FileSystemResource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }
}
