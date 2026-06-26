package com.company.knowledge.chunk;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.common.util.RAGFlow;
import com.company.knowledge.integration.ragflow.ChunkApi;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chunk 业务层。封装 {@link ChunkApi}。
 *
 * <p>核心能力是「审核发布」：通过 {@link #setAvailability} 切换 chunk 的 available 字段，
 * 控制其是否进入向量检索索引，实现草稿/发布/下线状态切换。
 */
@Service
@RequiredArgsConstructor
public class ChunkService {

    private final ChunkApi chunkApi;

    /**
     * 列出 chunk。
     *
     * @param datasetId dataset ID
     * @param docId     文档 ID
     * @param keywords  内容关键字，可为 null
     */
    public Map<String, Object> list(String datasetId, String docId, String keywords) {
        JsonNode resp = chunkApi.list(datasetId, docId, keywords, 1, 100);
        JsonNode data = RAGFlow.extractData(resp);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunks", RAGFlow.toCamelCaseList(data != null ? data.path("chunks") : null));
        result.put("total", data != null ? data.path("total").asInt(0) : 0);
        return result;
    }

    /**
     * 获取单个 chunk。
     */
    public Map<String, Object> get(String datasetId, String docId, String chunkId) {
        JsonNode resp = chunkApi.get(datasetId, docId, chunkId);
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    /**
     * 更新 chunk 文本内容。
     */
    public Map<String, Object> updateContent(String datasetId, String docId, String chunkId, String content) {
        if (content == null) {
            throw BizException.of(4002, "content is required");
        }
        chunkApi.update(datasetId, docId, chunkId, Map.of("content", content));
        return Map.of("success", true);
    }

    /**
     * 更新 chunk 的 important_keywords。
     */
    public Map<String, Object> updateKeywords(String datasetId, String docId, String chunkId, List<String> keywords) {
        List<String> safe = keywords == null ? Collections.emptyList() : keywords;
        chunkApi.update(datasetId, docId, chunkId, Map.of("important_keywords", safe));
        return Map.of("success", true);
    }

    /**
     * 更新 chunk 的 questions（embedding 时的辅助问句）。
     */
    public Map<String, Object> updateQuestions(String datasetId, String docId, String chunkId, List<String> questions) {
        List<String> safe = questions == null ? Collections.emptyList() : questions;
        chunkApi.update(datasetId, docId, chunkId, Map.of("questions", safe));
        return Map.of("success", true);
    }

    /**
     * 更新 chunk 的 tag_kwd（业务标签）。
     */
    public Map<String, Object> updateTags(String datasetId, String docId, String chunkId, List<String> tags) {
        List<String> safe = tags == null ? Collections.emptyList() : tags;
        chunkApi.update(datasetId, docId, chunkId, Map.of("tag_kwd", safe));
        return Map.of("success", true);
    }

    /**
     * 批量切换 chunk 可用性（审核发布关键 API）。
     *
     * @param available true=已发布（可检索），false=下线（不可检索）
     */
    public Map<String, Object> setAvailability(String datasetId, String docId, List<String> chunkIds, boolean available) {
        chunkApi.updateAvailability(datasetId, docId, chunkIds, available);
        return Map.of("success", true);
    }

    /**
     * 删除 chunks。
     */
    public Map<String, Object> delete(String datasetId, String docId, List<String> chunkIds) {
        chunkApi.delete(datasetId, docId, chunkIds);
        return Map.of("success", true);
    }

    /**
     * 工具方法：把逗号分隔字符串转成 List。
     */
    public static List<String> splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(csv.split("\\s*,\\s*"));
    }
}
