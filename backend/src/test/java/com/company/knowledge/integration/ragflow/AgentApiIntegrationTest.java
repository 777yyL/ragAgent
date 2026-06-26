package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AgentApi Spring 集成测试。
 *
 * <p>需要真实 RAGFlow，通过 {@code RAGFLOW_API_KEY} 触发；未配置时整体跳过。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAGFLOW_API_KEY", matches = "ragflow-.+")
class AgentApiIntegrationTest {

    @Autowired
    AgentApi agentApi;

    @Test
    void listAgents_shouldReturnNode() {
        JsonNode result = agentApi.listAgents();
        assertNotNull(result);
    }
}
