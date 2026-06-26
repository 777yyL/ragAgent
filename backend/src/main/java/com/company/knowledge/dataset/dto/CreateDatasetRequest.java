package com.company.knowledge.dataset.dto;

import lombok.Data;

import java.util.Map;

/**
 * 创建 Dataset 请求。
 *
 * <p>对应 RAGFlow {@code POST /api/v1/datasets}，支持以下字段：
 * <ul>
 *   <li>{@code name}：知识库名称（必填）</li>
 *   <li>{@code description}：描述</li>
 *   <li>{@code embedding_model}：embedding 模型，留空则用 RAGFlow 默认</li>
 *   <li>{@code chunk_method}：切片方法，默认 {@code naive}</li>
 *   <li>{@code parser_config}：高级解析配置，含
 *       {@code raptor}/{@code graphrag}/{@code parent_child}/
 *       {@code auto_keywords}/{@code auto_questions} 等开关</li>
 * </ul>
 */
@Data
public class CreateDatasetRequest {

    /** 知识库名称（必填） */
    private String name;
    /** 描述 */
    private String description;
    /** embedding 模型 ID/名称 */
    private String embeddingModel;
    /** 切片方法，如 naive/qa/paper/manual 等 */
    private String chunkMethod;
    /**
     * 解析器配置透传。允许调用方传任意结构，包括
     * raptor/graphrag/parent_child/auto_keywords/auto_questions 等。
     */
    private Map<String, Object> parserConfig;
}
