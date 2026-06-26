package com.company.knowledge.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 对话请求。
 *
 * <p>对应 RAGFlow {@code POST /api/v1/chats/{chatId}/completions}。
 */
@Data
public class ConverseRequest {

    /** 用户问题（必填，question 与 messages 二选一） */
    private String question;
    /** 会话 ID（可选，不传则 RAGFlow 自动创建新会话） */
    private String sessionId;
    /** 是否流式输出，默认 false */
    private Boolean stream = Boolean.FALSE;
    /** 覆盖 dataset_ids（可选，用于本次对话临时限定检索范围） */
    private List<String> datasetIds;
}
