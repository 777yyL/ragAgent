package com.company.knowledge.chunk.controller;

import com.company.knowledge.chunk.dto.AnnotationUpdateRequest;
import com.company.knowledge.chunk.service.AnnotationService;
import com.company.knowledge.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 人工标注 REST API。
 *
 * <p>路径前缀：{@code /api/datasets/{datasetId}/documents/{docId}/annotations}
 *
 * <ul>
 *   <li>GET    /pending?page=&pageSize= — 待标注 chunk 列表</li>
 *   <li>GET    /{chunkId}                — chunk 标注详情</li>
 *   <li>PUT    /{chunkId}                — 更新标注字段</li>
 *   <li>POST   /{chunkId}/mark           — 标记标注完成（available=true）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/datasets/{datasetId}/documents/{docId}/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    /**
     * 待标注 chunk 列表。
     */
    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> listPending(@PathVariable String datasetId,
                                              @PathVariable String docId,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer pageSize) {
        return Result.success(annotationService.listPending(datasetId, docId, page, pageSize));
    }

    /**
     * 获取 chunk 标注详情。
     */
    @GetMapping("/{chunkId}")
    public Result<Map<String, Object>> getChunkDetail(@PathVariable String datasetId,
                                           @PathVariable String docId,
                                           @PathVariable String chunkId) {
        return Result.success(annotationService.getChunkDetail(datasetId, docId, chunkId));
    }

    /**
     * 更新 chunk 标注字段。
     *
     * <p>请求体字段全部可选，仅传需要更新的字段。
     */
    @PutMapping("/{chunkId}")
    public Result<Map<String, Object>> updateAnnotation(@PathVariable String datasetId,
                                             @PathVariable String docId,
                                             @PathVariable String chunkId,
                                             @RequestBody AnnotationUpdateRequest req) {
        return Result.success(annotationService.updateAnnotation(datasetId, docId, chunkId, req));
    }

    /**
     * 标记 chunk 标注完成（available=true → 进入向量检索索引）。
     */
    @org.springframework.web.bind.annotation.PostMapping("/{chunkId}/mark")
    public Result<Map<String, Object>> markAnnotated(@PathVariable String datasetId,
                                          @PathVariable String docId,
                                          @PathVariable String chunkId) {
        return Result.success(annotationService.markAnnotated(datasetId, docId, chunkId));
    }
}
