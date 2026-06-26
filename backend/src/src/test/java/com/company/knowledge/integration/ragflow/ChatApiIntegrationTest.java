package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ChatApi Spring 集成测试。
 *
 * <p>需要真实 RAGFlow，通过 {@code RAGFLOW_API_KEY} 触发；未配置时整体跳过，不阻塞 CI。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAGFLOW_API_KEY", matches = "ragflow-.+")
class ChatApiIntegrationTest {

    @Autowired
    ChatApi chatApi;

    @Test
    void listChatAssistants_shouldReturnNode() {
        JsonNode result = chatApi.listChatAssistants();
        assertNotNull(result);
    }

    @Test
    void listSessions_shouldReturnNode() {
        JsonNode result = chatApi.listSessions("nonexistent-chat-id");
        assertNotNull(result);
    }
}
