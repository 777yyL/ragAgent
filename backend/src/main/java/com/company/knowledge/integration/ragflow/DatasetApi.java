package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import com.company.knowledge.common.exception.BizException;

/**
 * RAGFlow {@code DATASET MANAGEMENT} 接口包装。
 *
 * <p>对应 RAGFlow 文档 §DATASET MANAGEMENT：
 * <ul>
 *   <li>{@code POST   /api/v1/datasets}              创建</li>
 *   <li>{@code GET    /api/v1/datasets}              列表（page/page_size）</li>
 *   <li>{@code PUT    /api/v1/datasets/{id}}          更新</li>
 *   <li>{@code DELETE /api/v1/datasets}              删除（ids=逗号分隔）</li>
 * </ul>
 *
 * <p>创建/更新时支持 parser_config 的 graphrag/raptor/parent_child/auto_keywords/auto_questions
 * 等高级开关，业务层调用时直接在 body 中传完整结构。
 */
@Component
@RequiredArgsConstructor
public class DatasetApi {

    private final RAGFlowClient client;

    public JsonNode list(int page, int pageSize) {
        return client.get("/api/v1/datasets", Map.of("page", page, "page_size", pageSize));
    }

    public JsonNode create(Map<String, Object> body) {
        return client.post("/api/v1/datasets", body);
    }

    public JsonNode update(String datasetId, Map<String, Object> body) {
        return client.put("/api/v1/datasets/" + datasetId, body);
    }

    public JsonNode delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BizException.of(4002, "ids cannot be empty");
        }
        // RAGFlow DELETE /api/v1/datasets 期望 JSON body {"ids": [...]}，不是 query param
        return client.deleteWithBody("/api/v1/datasets", Map.of("ids", ids));
    }
}
