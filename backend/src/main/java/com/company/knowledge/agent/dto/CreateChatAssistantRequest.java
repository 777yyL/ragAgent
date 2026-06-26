package com.company.knowledge.agent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 chat assistant 请求。
 *
 * <p>对应 RAGFlow {@code POST /api/v1/chats}。
 */
@Data
public class CreateChatAssistantRequest {

    /** 助手名称（必填） */
    private String name;
    /** 关联的 dataset IDs */
    private List<String> datasetIds;
    /** LLM 配置（model/temperature/maxTokens 等） */
    private Llm llm;
    /** Prompt 配置，透传到 RAGFlow {@code prompt_config}（任意结构） */
    private Map<String, Object> prompt;
    /** Base64 图标，可选 */
    private String icon;
    /** 相似度阈值 */
    private Double similarityThreshold;
    /** TopK */
    private Integer topK;

    /**
     * LLM 配置子对象。
     */
    @Data
    public static class Llm {
        /** 模型 ID，如 qwen-plus@Tongyi-Qianwen */
        private String model;
        /** 采样温度 */
        private Double temperature;
        /** 最大 tokens */
        private Integer maxTokens;
    }
}
