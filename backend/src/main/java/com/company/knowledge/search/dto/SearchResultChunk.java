package com.company.knowledge.search.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 检索结果中的单个 chunk。
 */
@Data
public class SearchResultChunk {

    /** chunk ID */
    private String chunkId;

    /** chunk 文本内容（若 highlight=true，可能含 {@code <em>} 标签） */
    private String content;

    /** 与查询的相似度（0~1） */
    private Double similarity;

    /** 文档 ID */
    private String documentId;

    /** 文档名（RAGFlow 的 document_keyword 字段） */
    private String documentName;

    /** dataset ID（RAGFlow 的 kb_id 字段） */
    private String datasetId;

    /** chunk 在原文档中的位置信息 */
    private List<Map<String, Object>> positions;

    /** 其它元数据（important_keywords/tag_kwd/highlight 等） */
    private Map<String, Object> metadata;
}
