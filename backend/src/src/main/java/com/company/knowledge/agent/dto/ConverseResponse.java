package com.company.knowledge.agent.dto;

import lombok.Data;

/**
 * 对话响应（含溯源引用）。
 *
 * <p>Phase 6.3 的溯源核心：{@link #reference} 内含 chunk 来源（文档/相似度/位置）。
 */
@Data
public class ConverseResponse {

    /** 模型回答 */
    private String answer;
    /** 溯源引用（可为空） */
    private Reference reference;
    /** RAGFlow 消息 ID */
    private String id;
    /** 会话 ID（首次对话可能在此返回） */
    private String sessionId;
}
