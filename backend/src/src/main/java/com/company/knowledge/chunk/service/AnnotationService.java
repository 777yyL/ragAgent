package com.company.knowledge.chunk.service;

import com.company.knowledge.chunk.dto.AnnotationUpdateRequest;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.common.util.RAGFlow;
import com.company.knowledge.integration.ragflow.ChunkApi;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工标注 Service。
 *
 * <p>核心场景：知识编辑员对 chunk 进行人工标注，补充：
 * <ul>
 *   <li>{@code important_keywords}：关键术语，提升向量检索召回</li>
 *   <li>{@code questions}：常见问句，增强 FAQ 类问答</li>
 *   <li>{@code tag_kwd}：业务标签（如「规程」「图纸」「案例」），用于分类筛选</li>
 *   <li>{@code content}：正文校对修正</li>
 * </ul>
 *
 * <p>标注完成后通过 {@link #markAnnotated} 把 chunk 标记为已发布（{@code available=true}），
 * 进入向量检索索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationService {

    /** listPending 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 30;
    /** listPending 最大每页大小 */
    private static final int MAX_PAGE_SIZE = 200;

    private final ChunkApi chunkApi;

    /**
     * 列出待标注的 chunks。
     *
     * <p>「待标注」判定（任一满足）：
     * <ul>
     *   <li>{@code available=false}（未发布）</li>
     *   <li>{@code important_keywords} 为空或全空字符串</li>
     * </ul>
     *
     * <p>实现：调 {@link ChunkApi#list} 拉取后内存过滤。RAGFlow 的 list chunk 接口
     * 目前不支持按 available/keywords 过滤，只能在内存判断。
     *
     * @param datasetId dataset ID
     * @param docId     文档 ID（可选，{@code null} 时由调用方先决定文档；本方法要求必填）
     * @param page      页码（1-based），{@code <=0} 取默认 1
     * @param pageSize  每页大小，{@code <=0} 取默认 30，上限 200
     * @return 待标注 chunk 的 Map 列表（camelCase）
     */
    public List<Map<String, Object>> listPending(String datasetId, String docId, Integer page, Integer pageSize) {
        if (datasetId == null || datasetId.isEmpty()) {
            throw BizException.of(4002, "datasetId is required");
        }
        if (docId == null || docId.isEmpty()) {
            throw BizException.of(4002, "docId is required");
        }
        int p = page == null || page <= 0 ? 1 : page;
        int ps = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        JsonNode resp = chunkApi.list(datasetId, docId, null, p, ps);
        JsonNode chunks = resp.path("data").path("chunks");
        if (!chunks.isArray()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> pending = new ArrayList<>();
        for (JsonNode c : chunks) {
            if (isPending(c)) {
                pending.add(RAGFlow.toCamelCaseMap(c));
            }
        }
        return pending;
    }

    /**
     * 判断单个 chunk 是否「待标注」。
     *
     * <p>满足任一：
     * <ul>
     *   <li>{@code available=false} 或字段缺失</li>
     *   <li>{@code important_keywords} 数组为空、或只含空字符串</li>
     * </ul>
     */
    private boolean isPending(JsonNode chunk) {
        // available 字段：默认 false（RAGFlow 未发布的 chunk）
        boolean available = chunk.path("available").asBoolean(false);
        if (!available) {
            return true;
        }
        // important_keywords
        JsonNode kws = chunk.path("important_keywords");
        if (!kws.isArray() || kws.size() == 0) {
            return true;
        }
        boolean allEmpty = true;
        for (JsonNode k : kws) {
            if (!k.asText("").trim().isEmpty()) {
                allEmpty = false;
                break;
            }
        }
        return allEmpty;
    }

    /**
     * 获取单个 chunk 的标注详情。
     *
     * <p>返回完整 chunk 的 Map，含 {@code content / importantKeywords / questions / tagKwd / available}。
     */
    public Map<String, Object> getChunkDetail(String datasetId, String docId, String chunkId) {
        requireIds(datasetId, docId, chunkId);
        JsonNode resp = chunkApi.get(datasetId, docId, chunkId);
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    /**
     * 更新 chunk 的标注字段（important_keywords/questions/tag_kwd/content）。
     *
     * <p>所有字段都是可选的，只有 {@link AnnotationUpdateRequest} 中非 null 的字段会被写入。
     */
    public Map<String, Object> updateAnnotation(String datasetId, String docId, String chunkId,
                                     AnnotationUpdateRequest req) {
        requireIds(datasetId, docId, chunkId);
        if (req == null) {
            throw BizException.of(4002, "annotation request body is required");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (req.getImportantKeywords() != null) {
            body.put("important_keywords", req.getImportantKeywords());
        }
        if (req.getQuestions() != null) {
            body.put("questions", req.getQuestions());
        }
        if (req.getTagKwd() != null) {
            body.put("tag_kwd", req.getTagKwd());
        }
        if (req.getContent() != null) {
            body.put("content", req.getContent());
        }

        if (body.isEmpty()) {
            throw BizException.of(4002, "no fields to update; at least one of "
                    + "importantKeywords/questions/tagKwd/content must be non-null");
        }

        chunkApi.update(datasetId, docId, chunkId, body);
        return Map.of("success", true);
    }

    /**
     * 标记 chunk 标注完成（设 {@code available=true}）。
     *
     * <p>标注完成 = chunk 进入向量检索索引，可被 SearchService 命中。
     */
    public Map<String, Object> markAnnotated(String datasetId, String docId, String chunkId) {
        requireIds(datasetId, docId, chunkId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", true);
        chunkApi.update(datasetId, docId, chunkId, body);
        return Map.of("success", true);
    }

    private void requireIds(String datasetId, String docId, String chunkId) {
        if (datasetId == null || datasetId.isEmpty()) {
            throw BizException.of(4002, "datasetId is required");
        }
        if (docId == null || docId.isEmpty()) {
            throw BizException.of(4002, "docId is required");
        }
        if (chunkId == null || chunkId.isEmpty()) {
            throw BizException.of(4002, "chunkId is required");
        }
    }
}
