package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatasetApi 集成测试。
 *
 * <p>需要真实 RAGFlow 可访问，通过环境变量 {@code RAGFLOW_URL} + {@code RAGFLOW_API_KEY}
 * 触发执行；未配置时整体跳过，不阻塞 CI。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAGFLOW_API_KEY", matches = "ragflow-.+")
class DatasetApiTest {

    @Autowired
    DatasetApi datasetApi;

    @Test
    void list_shouldReturnCodeZero() {
        JsonNode result = datasetApi.list(1, 10);
        assertNotNull(result);
        assertEquals(0, result.path("code").asInt(-1));
    }
}
