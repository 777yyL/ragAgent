package com.company.knowledge.search.dto;

import lombok.Data;

import java.util.List;

/**
 * 检索请求 DTO（用户视角，不含权限元数据）。
 *
 * <p>权限预过滤由 {@code SearchService} 通过 {@code PermissionIndexService.buildMetadataCondition}
 * 自动注入，调用方无需关心。
 */
@Data
public class SearchQuery {

    /** 用户查询语句，必填 */
    private String question;

    /** 目标 dataset_ids；为空时全库检索（受权限元数据过滤） */
    private List<String> datasetIds;

    /** 返回 chunk 数量，默认 10 */
    private Integer topK = 10;

    /** 最小相似度阈值，默认 0.2 */
    private Double similarityThreshold = 0.2;

    /** 向量相似度权重（0~1），null 表示由 RAGFlow 用默认 0.3 */
    private Double vectorSimilarityWeight;

    /** rerank 模型 ID，null 表示不 rerank */
    private String rerankId;

    /** 是否启用知识图谱多跳检索，默认 false */
    private Boolean useKg = false;

    /** 是否启用关键词匹配，默认 true */
    private Boolean keyword = true;

    /** 是否高亮命中词，默认 true */
    private Boolean highlight = true;

    /** 跨语言检索的目标语言列表，null 表示不跨语言 */
    private List<String> crossLanguages;
}
