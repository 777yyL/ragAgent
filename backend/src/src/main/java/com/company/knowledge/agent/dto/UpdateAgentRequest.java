package com.company.knowledge.agent.dto;

import lombok.Data;

import java.util.Map;

/**
 * 更新 Agent 请求（全字段可选，partial update）。
 *
 * <p>对应 RAGFlow {@code PUT /api/v1/agents/{id}}：仅传入字段会被更新。
 */
@Data
public class UpdateAgentRequest {

    /** Agent 标题 */
    private String title;
    /** Canvas DSL 配置（大 JSON） */
    private Map<String, Object> canvasConfig;
    /** 描述 */
    private String description;
    /** Base64 编码的头像 */
    private String avatar;
}
