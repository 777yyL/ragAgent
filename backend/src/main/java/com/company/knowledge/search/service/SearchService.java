package com.company.knowledge.search.service;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.integration.ragflow.RetrievalApi;
import com.company.knowledge.permission.service.PermissionIndexService;
import com.company.knowledge.search.dto.SearchQuery;
import com.company.knowledge.search.dto.SearchResult;
import com.company.knowledge.search.dto.SearchResultChunk;
import com.company.knowledge.search.entity.SearchLog;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限预过滤检索 Service。
 *
 * <p>核心流程：
 * <ol>
 *   <li>从 {@link UserContext} 取当前用户 personId</li>
 *   <li>调 {@link PermissionIndexService#buildMetadataCondition} 拿权限元数据条件</li>
 *   <li>拼装 {@link RetrievalApi#retrieve} 请求体（含 metadata_condition + 用户参数）</li>
 *   <li>调 RAGFlow retrieval 接口</li>
 *   <li>异步写 search_log（通过 {@link SearchLogService#record}）</li>
 *   <li>返回 {@link SearchResult}</li>
 * </ol>
 *
 * <p>权限元数据条件（visible_depts / security_level / category 等）由
 * {@code PermissionIndexService} 预计算并注入到 RAGFlow 的 metadata_condition 中，
 * 在向量库内部完成预过滤，调用方无感知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    /** 默认取 TopN chunk 的 ID 用于埋点 */
    private static final int TOP_CHUNK_LOG_LIMIT = 5;

    private final RetrievalApi retrievalApi;
    private final PermissionIndexService permissionIndexService;
    private final SearchLogService searchLogService;

    /**
     * 执行一次带权限预过滤的检索。
     *
     * @param query 用户检索参数（question / datasetIds / topK / similarityThreshold 等）
     * @return 检索结果
     */
    public SearchResult search(SearchQuery query) {
        UserContext.CurrentUser user = UserContext.require();
        return doSearch(query, user.getPersonId(), "USER");
    }

    /**
     * 内部共用检索逻辑（用户检索与管理员测试共用）。
     *
     * @param query    检索参数
     * @param personId 操作者 personId
     * @param type     记录类型：USER / TEST
     */
    public SearchResult doSearch(SearchQuery query, String personId, String type) {
        long start = System.currentTimeMillis();

        // 1. 取权限元数据条件（USER 类型才注入；TEST 类型不过滤）
        Map<String, Object> metadataCondition;
        if ("USER".equals(type)) {
            metadataCondition = permissionIndexService.buildMetadataCondition(personId);
        } else {
            metadataCondition = null;
        }

        // 2. 拼 RAGFlow retrieval 请求
        Map<String, Object> body = buildRequestBody(query, metadataCondition);

        // 3. 调 RAGFlow
        JsonNode resp = retrievalApi.retrieve(body);

        // 4. 解析响应
        List<SearchResultChunk> chunks = parseChunks(resp);
        int total = chunks.size();

        long elapsed = System.currentTimeMillis() - start;

        // 5. 异步埋点
        recordLog(query, personId, type, total, chunks, elapsed);

        // 6. 组装结果
        SearchResult result = new SearchResult();
        result.setChunks(chunks);
        result.setTotal(total);
        result.setResponseMs(elapsed);
        return result;
    }

    /**
     * 组装 RAGFlow retrieval 请求体。
     */
    private Map<String, Object> buildRequestBody(SearchQuery query, Map<String, Object> metadataCondition) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", query.getQuestion());

        if (query.getDatasetIds() != null && !query.getDatasetIds().isEmpty()) {
            body.put("dataset_ids", query.getDatasetIds());
        }
        if (query.getTopK() != null) {
            body.put("top_k", query.getTopK());
            body.put("page_size", query.getTopK());
        }
        if (query.getSimilarityThreshold() != null) {
            body.put("similarity_threshold", query.getSimilarityThreshold());
        }
        if (query.getVectorSimilarityWeight() != null) {
            body.put("vector_similarity_weight", query.getVectorSimilarityWeight());
        }
        if (query.getRerankId() != null && !query.getRerankId().isEmpty()) {
            body.put("rerank_id", query.getRerankId());
        }
        if (query.getUseKg() != null) {
            body.put("use_kg", query.getUseKg());
        }
        if (query.getKeyword() != null) {
            body.put("keyword", query.getKeyword());
        }
        if (query.getHighlight() != null) {
            body.put("highlight", query.getHighlight());
        }
        if (query.getCrossLanguages() != null && !query.getCrossLanguages().isEmpty()) {
            body.put("cross_languages", query.getCrossLanguages());
        }
        if (metadataCondition != null && !isEmptyCondition(metadataCondition)) {
            body.put("metadata_condition", metadataCondition);
        }
        return body;
    }

    /**
     * 判断 metadata_condition 是否为空（conditions 列表为空）。
     */
    @SuppressWarnings("unchecked")
    private boolean isEmptyCondition(Map<String, Object> mc) {
        Object conditions = mc.get("conditions");
        if (conditions instanceof List) {
            return ((List<Object>) conditions).isEmpty();
        }
        return true;
    }

    /**
     * 解析 RAGFlow 响应为 {@link SearchResultChunk} 列表。
     *
     * <p>RAGFlow 响应结构：{@code {code:0, data:{chunks:[{id,content,similarity,document_id,document_keyword,kb_id,positions,...}]}}}
     */
    private List<SearchResultChunk> parseChunks(JsonNode resp) {
        if (resp == null) {
            return Collections.emptyList();
        }
        JsonNode data = resp.path("data");
        JsonNode chunksNode = data.path("chunks");
        if (!chunksNode.isArray() || chunksNode.size() == 0) {
            return Collections.emptyList();
        }

        List<SearchResultChunk> result = new ArrayList<>(chunksNode.size());
        for (JsonNode c : chunksNode) {
            SearchResultChunk chunk = new SearchResultChunk();
            chunk.setChunkId(c.path("id").asText(null));
            chunk.setContent(c.path("content").asText(null));
            chunk.setSimilarity(c.has("similarity") ? c.path("similarity").asDouble() : null);
            chunk.setDocumentId(c.path("document_id").asText(null));
            chunk.setDocumentName(c.path("document_keyword").asText(null));
            chunk.setDatasetId(c.path("kb_id").asText(null));

            // positions
            JsonNode posNode = c.path("positions");
            if (posNode.isArray() && posNode.size() > 0) {
                List<Map<String, Object>> positions = new ArrayList<>(posNode.size());
                for (JsonNode p : posNode) {
                    positions.add(jsonNodeToMap(p));
                }
                chunk.setPositions(positions);
            }

            // metadata：除了已提取的字段外，剩余都塞到 metadata
            Map<String, Object> metadata = jsonNodeToMap(c);
            // 移除已显式提取的字段，避免重复
            metadata.remove("id");
            metadata.remove("content");
            metadata.remove("similarity");
            metadata.remove("document_id");
            metadata.remove("document_keyword");
            metadata.remove("kb_id");
            metadata.remove("positions");
            chunk.setMetadata(metadata);

            result.add(chunk);
        }
        return result;
    }

    /**
     * JsonNode → Map（仅对 object/array/scalar 做宽松转换）。
     */
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isNumber()) {
                if (v.isInt()) {
                    map.put(e.getKey(), v.intValue());
                } else {
                    map.put(e.getKey(), v.doubleValue());
                }
            } else if (v.isBoolean()) {
                map.put(e.getKey(), v.booleanValue());
            } else if (v.isTextual()) {
                map.put(e.getKey(), v.textValue());
            } else if (v.isArray()) {
                List<Object> list = new ArrayList<>(v.size());
                for (JsonNode item : v) {
                    if (item.isObject()) {
                        list.add(jsonNodeToMap(item));
                    } else if (item.isTextual()) {
                        list.add(item.textValue());
                    } else if (item.isNumber()) {
                        list.add(item.isInt() ? item.intValue() : item.doubleValue());
                    } else if (item.isBoolean()) {
                        list.add(item.booleanValue());
                    } else {
                        list.add(item.toString());
                    }
                }
                map.put(e.getKey(), list);
            } else if (v.isObject()) {
                map.put(e.getKey(), jsonNodeToMap(v));
            } else if (!v.isNull()) {
                map.put(e.getKey(), v.toString());
            }
        });
        return map;
    }

    /**
     * 异步记录检索日志。
     */
    private void recordLog(SearchQuery query, String personId, String type,
                           int total, List<SearchResultChunk> chunks, long elapsed) {
        try {
            SearchLog logEntry = new SearchLog();
            logEntry.setUserId(personId);
            logEntry.setQuery(query.getQuestion());
            if (query.getDatasetIds() != null) {
                logEntry.setDatasetIds(query.getDatasetIds().toArray(new String[0]));
            }
            logEntry.setResultCount(total);
            logEntry.setResponseMs((int) elapsed);
            logEntry.setType(type);
            logEntry.setCreatedAt(LocalDateTime.now());

            // top chunk ids：取前 N 个
            int limit = Math.min(TOP_CHUNK_LOG_LIMIT, chunks.size());
            if (limit > 0) {
                String[] topIds = new String[limit];
                for (int i = 0; i < limit; i++) {
                    topIds[i] = chunks.get(i).getChunkId();
                }
                logEntry.setTopChunkIds(topIds);
            }

            searchLogService.record(logEntry);
        } catch (Exception e) {
            log.warn("record search log failed: {}", e.getMessage());
        }
    }
}
