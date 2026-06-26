package com.company.knowledge.dataset;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.dataset.dto.CreateDatasetRequest;
import com.company.knowledge.dataset.dto.UpdateDatasetRequest;
import com.company.knowledge.integration.ragflow.DatasetApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Dataset 业务层。封装 {@link DatasetApi}，做参数校验与字段映射。
 *
 * <p>核心职责：把 RAGFlow 的原始响应（snake_case + 嵌套 code/data 结构）
 * 转成前端期望的扁平 camelCase 结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetApi datasetApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 列表查询。返回 {@code {datasets: [...], total: N}}。
     */
    public Map<String, Object> list(int page, int pageSize, String keywords) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 200) pageSize = 30;

        JsonNode resp = datasetApi.list(page, pageSize);
        return parseListResponse(resp);
    }

    /**
     * 创建 dataset。返回新建的 dataset 对象。
     */
    public Map<String, Object> create(CreateDatasetRequest req) {
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
            throw BizException.of(4002, "name is required");
        }
        JsonNode resp = datasetApi.create(toBody(req));
        // RAGFlow create 返回 {code:0, data:{...dataset...}}
        JsonNode dataNode = resp.path("data");
        if (dataNode.isObject()) {
            return convertDataset(dataNode);
        }
        return Collections.emptyMap();
    }

    public Map<String, Object> update(String id, UpdateDatasetRequest req) {
        if (id == null || id.isEmpty()) throw BizException.of(4002, "id is required");
        if (req == null) throw BizException.of(4002, "body is required");
        datasetApi.update(id, toBody(req));
        return Collections.singletonMap("id", id);
    }

    public Map<String, Object> delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) throw BizException.of(4002, "ids cannot be empty");
        datasetApi.delete(ids);
        return Collections.singletonMap("deleted", ids.size());
    }

    // ==================== RAGFlow 响应解析 ====================

    /**
     * 解析 RAGFlow 列表响应。
     * <p>RAGFlow 返回 {@code {code:0, data:[...], total_datasets:N}}
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseListResponse(JsonNode resp) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> datasets = new ArrayList<>();

        JsonNode dataNode = resp.path("data");
        if (dataNode.isArray()) {
            for (JsonNode item : dataNode) {
                datasets.add(convertDataset(item));
            }
        }

        result.put("datasets", datasets);
        // RAGFlow 用 total_datasets，部分版本用 total
        int total = resp.path("total_datasets").asInt(resp.path("total").asInt(datasets.size()));
        result.put("total", total);
        return result;
    }

    /**
     * 把 RAGFlow 的 snake_case dataset 节点转成前端期望的 camelCase Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertDataset(JsonNode node) {
        try {
            Map<String, Object> raw = objectMapper.treeToValue(node, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();

            result.put("id", getStr(raw, "id"));
            result.put("name", getStr(raw, "name"));
            result.put("description", getStr(raw, "description"));
            result.put("chunkMethod", getStr(raw, "chunk_method"));
            result.put("embeddingModel", getStr(raw, "embedding_model"));
            result.put("documentCount", getInt(raw, "document_count"));
            result.put("chunkCount", getInt(raw, "chunk_count"));
            result.put("permission", getStr(raw, "permission"));
            result.put("createDate", getStr(raw, "create_date"));
            result.put("updateDate", getStr(raw, "update_date"));
            result.put("language", getStr(raw, "language"));

            // parser_config 透传（含 raptor/graphrag/auto_keywords 等）
            Object parserConfig = raw.get("parser_config");
            if (parserConfig != null) {
                result.put("parserConfig", parserConfig);
            }

            return result;
        } catch (Exception e) {
            log.warn("convertDataset failed: {}", e.getMessage());
            // fallback：直接返回原始 Map
            try {
                return objectMapper.treeToValue(node, Map.class);
            } catch (Exception e2) {
                return Collections.emptyMap();
            }
        }
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    // ==================== DTO → RAGFlow body ====================

    private static Map<String, Object> toBody(CreateDatasetRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", req.getName());
        if (req.getDescription() != null) body.put("description", req.getDescription());
//        if (req.getEmbeddingModel() != null && !req.getEmbeddingModel().isEmpty())
//            body.put("embedding_model", req.getEmbeddingModel());
        if (req.getChunkMethod() != null && !req.getChunkMethod().isEmpty())
            body.put("chunk_method", req.getChunkMethod());
        if (req.getParserConfig() != null && !req.getParserConfig().isEmpty())
            body.put("parser_config", req.getParserConfig());
        return body;
    }

    private static Map<String, Object> toBody(UpdateDatasetRequest req) {
        Map<String, Object> body = new HashMap<>();
        if (req.getName() != null) body.put("name", req.getName());
        if (req.getDescription() != null) body.put("description", req.getDescription());
        if (req.getEmbeddingModel() != null && !req.getEmbeddingModel().isEmpty())
            body.put("embedding_model", req.getEmbeddingModel());
        if (req.getChunkMethod() != null && !req.getChunkMethod().isEmpty())
            body.put("chunk_method", req.getChunkMethod());
        if (req.getParserConfig() != null && !req.getParserConfig().isEmpty())
            body.put("parser_config", req.getParserConfig());
        return body;
    }
}
