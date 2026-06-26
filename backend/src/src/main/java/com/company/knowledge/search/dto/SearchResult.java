package com.company.knowledge.search.dto;

import lombok.Data;

import java.util.List;

/**
 * 检索结果。
 */
@Data
public class SearchResult {

    /** 命中的 chunk 列表（按相似度降序） */
    private List<SearchResultChunk> chunks;

    /** 命中总数 */
    private int total;

    /** 本次检索的端到端响应耗时（毫秒） */
    private long responseMs;
}
