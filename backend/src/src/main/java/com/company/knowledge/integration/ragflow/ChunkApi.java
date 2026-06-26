package com.company.knowledge.integration.ragflow;

import com.company.knowledge.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAGFlow {@code CHUNK MANAGEMENT WITHIN DATASET} 接口包装。
 *
 * <p>覆盖 chunk 的新增/列表/详情/更新/批量切换可用性/删除共 6 个端点。
 */
@Component
@RequiredArgsConstructor
public class ChunkApi {

    private final RAGFlowClient client;

    /**
     * 新增 chunk。
     *
     * @param datasetId dataset ID
     * @param docId     文档 ID
     * @param body      含 content/important_keywords/questions/tag_kwd/image_base64 等
     */
    public JsonNode add(String datasetId, String docId, Map<String, Object> body) {
        return client.post("/api/v1/datasets/" + datasetId + "/documents/" + docId + "/chunks", body);
    }

    /**
     * 列出 chunk。
     *
     * @param datasetId dataset ID
     * @param docId     文档 ID
     * @param keywords  内容关键字，可为 null
     * @param page      页码，{@code <=0} 时取默认
     * @param pageSize  每页条数，{@code <=0} 时取默认
     */
    public JsonNode list(String datasetId, String docId, String keywords, Integer page, Integer pageSize) {
        Map<String, Object> params = new HashMap<>();
        if (keywords != null && !keywords.isEmpty()) {
            params.put("keywords", keywords);
        }
        if (page != null && page > 0) {
            params.put("page", page);
        }
        if (pageSize != null && pageSize > 0) {
            params.put("page_size", pageSize);
        }
        return client.get("/api/v1/datasets/" + datasetId + "/documents/" + docId + "/chunks", params);
    }

    /**
     * 获取单个 chunk 详情。
     */
    public JsonNode get(String datasetId, String docId, String chunkId) {
        return client.get("/api/v1/datasets/" + datasetId + "/documents/" + docId + "/chunks/" + chunkId, null);
    }

    /**
     * 更新单个 chunk 的字段（content/important_keywords/questions/tag_kwd/available/positions）。
     */
    public JsonNode update(String datasetId, String docId, String chunkId, Map<String, Object> body) {
        return client.patch("/api/v1/datasets/" + datasetId + "/documents/" + docId + "/chunks/" + chunkId, body);
    }

    /**
     * 批量切换 chunk 可用性（审核发布核心）。
     *
     * @param available true=可用（已发布），false=不可用（草稿/下线）
     */
    public JsonNode updateAvailability(String datasetId, String docId, List<String> chunkIds, boolean available) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            throw BizException.of(4002, "chunk_ids cannot be empty");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("chunk_ids", chunkIds);
        body.put("available", available);
        return client.patch("/api/v1/datasets/" + datasetId + "/documents/" + docId + "/chunks", body);
    }

    /**
     * 删除 chunks。
     */
    public JsonNode delete(String datasetId, String docId, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            throw BizException.of(4002, "chunk_ids cannot be empty");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("chunk_ids", chunkIds);
        return client.deleteWithBody("/api/v1/datasets/" + datasetId + "/documents/" + docId + "/chunks", body);
    }
}
