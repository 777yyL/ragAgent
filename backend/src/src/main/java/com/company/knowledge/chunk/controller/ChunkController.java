package com.company.knowledge.chunk.controller;

import com.company.knowledge.chunk.ChunkService;
import com.company.knowledge.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chunk REST API。
 *
 * <p>路径前缀：{@code /api/datasets/{datasetId}/documents/{docId}/chunks}
 *
 * <ul>
 *   <li>GET    列表</li>
 *   <li>GET    /{chunkId}                              详情</li>
 *   <li>PATCH  /{chunkId}/content                      改正文</li>
 *   <li>PATCH  /{chunkId}/keywords|questions|tags      改扩展字段</li>
 *   <li>PATCH  /availability?ids=&available=true|false 切换可用性（审核发布）</li>
 *   <li>DELETE ?ids=                                    删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/datasets/{datasetId}/documents/{docId}/chunks")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkService chunkService;

    @GetMapping
    public Result<Map<String, Object>> list(@PathVariable String datasetId,
                                 @PathVariable String docId,
                                 @RequestParam(required = false) String keywords) {
        return Result.success(chunkService.list(datasetId, docId, keywords));
    }

    @GetMapping("/{chunkId}")
    public Result<Map<String, Object>> get(@PathVariable String datasetId,
                                @PathVariable String docId,
                                @PathVariable String chunkId) {
        return Result.success(chunkService.get(datasetId, docId, chunkId));
    }

    @PatchMapping("/{chunkId}/content")
    public Result<Map<String, Object>> updateContent(@PathVariable String datasetId,
                                          @PathVariable String docId,
                                          @PathVariable String chunkId,
                                          @RequestBody Map<String, String> body) {
        return Result.success(chunkService.updateContent(datasetId, docId, chunkId, body.get("content")));
    }

    @PatchMapping("/{chunkId}/keywords")
    public Result<Map<String, Object>> updateKeywords(@PathVariable String datasetId,
                                           @PathVariable String docId,
                                           @PathVariable String chunkId,
                                           @RequestBody Map<String, List<String>> body) {
        return Result.success(chunkService.updateKeywords(datasetId, docId, chunkId, body.get("keywords")));
    }

    @PatchMapping("/{chunkId}/questions")
    public Result<Map<String, Object>> updateQuestions(@PathVariable String datasetId,
                                            @PathVariable String docId,
                                            @PathVariable String chunkId,
                                            @RequestBody Map<String, List<String>> body) {
        return Result.success(chunkService.updateQuestions(datasetId, docId, chunkId, body.get("questions")));
    }

    @PatchMapping("/{chunkId}/tags")
    public Result<Map<String, Object>> updateTags(@PathVariable String datasetId,
                                       @PathVariable String docId,
                                       @PathVariable String chunkId,
                                       @RequestBody Map<String, List<String>> body) {
        return Result.success(chunkService.updateTags(datasetId, docId, chunkId, body.get("tags")));
    }

    /**
     * 批量切换 chunk 可用性。{@code available=true} 表示发布（可被检索）。
     * 接收 JSON body: {"chunkIds":["id1","id2"], "available": true}
     */
    @PatchMapping("/availability")
    public Result<Map<String, Object>> setAvailability(@PathVariable String datasetId,
                                            @PathVariable String docId,
                                            @RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("chunkIds");
        if (ids == null) ids = (List<String>) body.get("ids");
        boolean available = Boolean.TRUE.equals(body.get("available"));
        return Result.success(chunkService.setAvailability(datasetId, docId, ids, available));
    }

    @DeleteMapping
    public Result<Map<String, Object>> delete(@PathVariable String datasetId,
                                   @PathVariable String docId,
                                   @RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("chunkIds");
        if (ids == null) ids = (List<String>) body.get("ids");
        return Result.success(chunkService.delete(datasetId, docId, ids));
    }
}
