package com.company.knowledge.search.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 检索测试请求 DTO（管理员调参用，不带权限过滤）。
 *
 * <p>用于运营/管理员调优 top_k / similarity_threshold / rerank_id 等参数，
 * 检索结果会写入 search_log（{@code type=TEST}）用于回看调参历史。
 *
 * <p>与 {@link SearchQuery} 的差异：
 * <ul>
 *   <li>{@code datasetId} 必填（单值），不支持跨库测试</li>
 *   <li>不经过 {@code PermissionIndexService.buildMetadataCondition}</li>
 *   <li>记录到 search_log 的 type 为 {@code TEST}</li>
 * </ul>
 */
@Data
public class RetrievalTestRequest {

    /** 目标 dataset ID，必填 */
    @NotEmpty
    private String datasetId;

    /** 检索语句，必填 */
    @NotEmpty
    private String question;

    /** 返回 chunk 数量，默认 10 */
    private Integer topK = 10;

    /** 最小相似度阈值，默认 0.2 */
    private Double similarityThreshold = 0.2;

    /** 向量相似度权重（0~1），{@code null} 表示由 RAGFlow 默认 */
    private Double vectorSimilarityWeight;

    /** rerank 模型 ID */
    private String rerankId;

    /** 是否启用知识图谱多跳检索，默认 false */
    private Boolean useKg = false;

    /** 是否启用关键词匹配，默认 true */
    private Boolean keyword = true;

    /** 是否高亮命中词，默认 true */
    private Boolean highlight = true;
}
