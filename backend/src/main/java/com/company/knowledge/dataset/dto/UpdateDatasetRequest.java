package com.company.knowledge.dataset.dto;

import lombok.Data;

import java.util.Map;

/**
 * 更新 Dataset 请求。所有字段可空（partial update）。
 */
@Data
public class UpdateDatasetRequest {

    private String name;
    private String description;
    private String embeddingModel;
    private String chunkMethod;
    /** 透传高级解析配置 */
    private Map<String, Object> parserConfig;
}
