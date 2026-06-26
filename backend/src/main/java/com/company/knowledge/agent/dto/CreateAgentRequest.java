package com.company.knowledge.agent.dto;

import lombok.Data;

import java.util.Map;

/**
 * 创建 Agent 请求。
 *
 * <p>对应 RAGFlow {@code POST /api/v1/agents}，支持以下字段：
 * <ul>
 *   <li>{@code title}：Agent 标题（必填）</li>
 *   <li>{@code canvasConfig}：Canvas DSL（大 JSON），透传为 RAGFlow 的 {@code dsl} 字段</li>
 *   <li>{@code description}：描述</li>
 *   <li>{@code avatar}：Base64 编码的头像</li>
 * </ul>
 */
@Data
public class CreateAgentRequest {

    /** Agent 标题（必填） */
    private String title;
    /**
     * Canvas DSL 配置（大 JSON）。
     * 类型用 Map，允许任意结构透传到 RAGFlow 的 {@code dsl} 字段。
     */
    private Map<String, Object> canvasConfig;
    /** 描述 */
    private String description;
    /** Base64 编码的头像 */
    private String avatar;
}
