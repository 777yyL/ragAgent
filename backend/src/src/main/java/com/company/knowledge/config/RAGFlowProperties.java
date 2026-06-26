package com.company.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAGFlow 连接配置。对应 application.yml 的 {@code ragflow.*} 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ragflow")
public class RAGFlowProperties {

    /** RAGFlow 服务地址，如 {@code http://localhost:9380} */
    private String baseUrl;

    /** RAGFlow API Key（Bearer Token），格式 {@code ragflow-xxx} */
    private String apiKey;
}
