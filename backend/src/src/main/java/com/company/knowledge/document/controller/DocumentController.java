package com.company.knowledge.document.controller;

import com.company.knowledge.common.result.Result;
import com.company.knowledge.document.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 文档 REST API。
 *
 * <p>路径前缀：{@code /api/datasets/{datasetId}/documents}
 *
 * <ul>
 *   <li>POST   上传（multipart）</li>
 *   <li>GET    列表</li>
 *   <li>DELETE 删除（ids）</li>
 *   <li>POST   /{docId}/reparse       重新解析</li>
 *   <li>GET    /{docId}/download      下载</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/datasets/{datasetId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文件。{@code isParse=true} 时上传后自动触发解析。
     */
    @PostMapping
    public Result<Map<String, Object>> upload(@PathVariable String datasetId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(name = "parse", defaultValue = "false") boolean parse) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "unnamed";
        }
        return Result.success(documentService.upload(datasetId, fileName, file.getBytes(), parse));
    }

    /**
     * 列表。
     */
    @GetMapping
    public Result<Map<String, Object>> list(@PathVariable String datasetId,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(name = "page_size", defaultValue = "30") int pageSize,
                                 @RequestParam(required = false) String keywords) {
        return Result.success(documentService.list(datasetId, page, pageSize, keywords));
    }

    /**
     * 删除。
     */
    @DeleteMapping
    public Result<Map<String, Object>> delete(@PathVariable String datasetId,
                                   @RequestParam("ids") List<String> ids) {
        return Result.success(documentService.delete(datasetId, ids));
    }

    /**
     * 重新解析：先停止再触发。
     */
    @PostMapping("/{docId}/reparse")
    public Result<Map<String, Object>> reparse(@PathVariable String datasetId,
                                    @PathVariable String docId) {
        return Result.success(documentService.reparse(datasetId, java.util.Collections.singletonList(docId)));
    }

    /**
     * 下载原始文件。
     */
    @GetMapping("/{docId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String datasetId,
                                           @PathVariable String docId) {
        byte[] bytes = documentService.download(datasetId, docId);
        String contentType = documentService.downloadContentType(datasetId, docId);
        String fileName = URLEncoder.encode(docId, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }
}
