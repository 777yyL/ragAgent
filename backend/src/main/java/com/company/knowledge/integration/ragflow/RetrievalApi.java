package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RAGFlow {@code POST /api/v1/retrieval} 接口包装。
 *
 * <p>向量检索 + 关键词检索 + rerank + metadata 预过滤，返回 chunks 数组。
 *
 * <p>请求体结构：
 * <ul>
 *   <li>{@code question} 必填</li>
 *   <li>{@code dataset_ids} 或 {@code document_ids}（至少一个）</li>
 *   <li>{@code similarity_threshold / vector_similarity_weight / top_k / rerank_id}</li>
 *   <li>{@code keyword / highlight / cross_languages}</li>
 *   <li>{@code metadata_condition}（权限预过滤核心，由 {@code PermissionIndexService.buildMetadataCondition} 构造）</li>
 *   <li>{@code use_kg / toc_enhance}</li>
 * </ul>
 *
 * <p>响应：{@code {code:0, data:{chunks:[...], doc_aggs:[...]}}}
 */
@Component
@RequiredArgsConstructor
public class RetrievalApi {

    private final RAGFlowClient client;

    /**
     * 发起一次检索请求。
     *
     * @param body 完整的请求体 Map（snake_case 键名，与 RAGFlow API 一致）
     * @return 原始响应 JsonNode（含 code/data）
     */
    public JsonNode retrieve(Map<String, Object> body) {
        return client.post("/api/v1/retrieval", body);
    }
}
