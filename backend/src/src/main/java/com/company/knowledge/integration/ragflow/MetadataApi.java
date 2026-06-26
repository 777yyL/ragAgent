package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAGFlow dataset metadata 接口包装。
 *
 * <p>包含 metadata summary 和 metadata 批量更新两个端点。
 * 批量更新是 Phase 5 权限策略落地到向量库的桥梁。
 */
@Component
@RequiredArgsConstructor
public class MetadataApi {

    private final RAGFlowClient client;

    /**
     * 获取 dataset 内 metadata 字段聚合摘要（用于筛选 UI）。
     */
    public JsonNode summary(String datasetId) {
        return client.get("/api/v1/datasets/" + datasetId + "/metadata/summary", null);
    }

    /**
     * 批量更新/删除文档 metadata。
     *
     * @param datasetId dataset ID
     * @param selector  {@code {"document_ids": [...], "metadata_condition": {...}}}
     * @param updates   {@code [{"key","match","value"}]}，可为 null
     * @param deletes   {@code [{"key","value"}]}，可为 null
     */
    public JsonNode update(String datasetId, Map<String, Object> selector,
                           List<Map<String, Object>> updates,
                           List<Map<String, Object>> deletes) {
        Map<String, Object> body = new java.util.HashMap<>();
        if (selector != null) {
            body.put("selector", selector);
        }
        if (updates != null && !updates.isEmpty()) {
            body.put("updates", updates);
        }
        if (deletes != null && !deletes.isEmpty()) {
            body.put("deletes", deletes);
        }
        return client.post("/api/v1/datasets/" + datasetId + "/metadata/update", body);
    }
}
