package com.company.knowledge.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 检索溯源 chunk。
 *
 * <p>对应 RAGFlow 对话响应 {@code reference.chunks[]} 的单条结构。这是 Phase 6.3 的溯源关键数据。
 */
@Data
public class ReferenceChunk {

    /** chunk ID */
    private String chunkId;
    /** chunk 文本内容 */
    private String content;
    /** 所属文档 ID */
    private String documentId;
    /** 所属文档名称 */
    private String documentName;
    /** 所属 dataset ID */
    private String datasetId;
    /** 相似度（0-1） */
    private Double similarity;
    /** chunk 在文档中的位置信息（页码、bbox 等，RAGFlow 原样透传） */
    private List<String> positions;
}
