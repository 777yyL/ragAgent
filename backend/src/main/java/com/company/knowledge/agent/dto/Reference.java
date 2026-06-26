package com.company.knowledge.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 对话溯源引用。
 *
 * <p>对应 RAGFlow 对话响应的 {@code reference} 对象，包含 chunks 列表（溯源关键数据）。
 */
@Data
public class Reference {

    /** chunk 总数（RAGFlow 返回的 {@code total}） */
    private Integer total;
    /** 检索到的 chunk 列表 */
    private List<ReferenceChunk> chunks;
}
