package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentApi 纯单元测试。集成测试见 {@link AgentApiIntegrationTest}。
 */
class AgentApiTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listAgents_withParams_buildsQuery() {
        RAGFlowClient client = mockClient();
        AgentApi api = new AgentApi(client);

        api.listAgents(1, 20, "a1", "title-x");

        verify(client).get(eq("/api/v1/agents"), argThat(m -> {
            Map<String, Object> p = (Map<String, Object>) m;
            return Integer.valueOf(1).equals(p.get("page"))
                    && Integer.valueOf(20).equals(p.get("page_size"))
                    && "a1".equals(p.get("id"))
                    && "title-x".equals(p.get("title"));
        }));
    }

    @Test
    void createAgent_postsToAgentsCollection() {
        RAGFlowClient client = mockClient();
        AgentApi api = new AgentApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("title", "t1");
        api.createAgent(body);

        verify(client).post(eq("/api/v1/agents"), same(body));
    }

    @Test
    void updateAgent_putsToAgentsIdPath() {
        RAGFlowClient client = mockClient();
        AgentApi api = new AgentApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("title", "t2");
        api.updateAgent("a1", body);

        verify(client).put(eq("/api/v1/agents/a1"), same(body));
    }

    @Test
    void deleteAgent_usesDeleteWithBody() {
        RAGFlowClient client = mockClient();
        AgentApi api = new AgentApi(client);

        api.deleteAgent("a1");

        verify(client).deleteWithBody(eq("/api/v1/agents/a1"), any());
    }

    @Test
    void createAgentSession_postsToSessionsPath() {
        RAGFlowClient client = mockClient();
        AgentApi api = new AgentApi(client);

        api.createAgentSession("a1", new HashMap<>());

        verify(client).post(eq("/api/v1/agents/a1/sessions"), anyMap());
    }

    @Test
    void converseWithAgent_injectsAgentIdAndStream() {
        RAGFlowClient client = mockClient();
        AgentApi api = new AgentApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("query", "hello");
        api.converseWithAgent("a1", "s1", body);

        verify(client).post(eq("/api/v1/agents/a1/completions"), argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return "a1".equals(m.get("agent_id"))
                    && "s1".equals(m.get("session_id"))
                    && Boolean.FALSE.equals(m.get("stream"))
                    && "hello".equals(m.get("query"));
        }));
    }

    @Test
    void converseWithAgent_respectsCallerStreamChoice() {
        RAGFlowClient client = mockClient();
        AgentApi api = new AgentApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("query", "hi");
        body.put("stream", true);
        api.converseWithAgent("a1", null, body);

        verify(client).post(eq("/api/v1/agents/a1/completions"), argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return Boolean.TRUE.equals(m.get("stream")) && !m.containsKey("session_id");
        }));
    }

    private RAGFlowClient mockClient() {
        RAGFlowClient client = mock(RAGFlowClient.class);
        try {
            JsonNode ok = mapper.readTree("{\"code\":0,\"data\":{}}");
            when(client.post(anyString(), any())).thenReturn(ok);
            when(client.put(anyString(), any())).thenReturn(ok);
            when(client.get(anyString(), any())).thenReturn(ok);
            when(client.delete(anyString(), any())).thenReturn(ok);
            when(client.deleteWithBody(anyString(), any())).thenReturn(ok);
            when(client.patch(anyString(), any())).thenReturn(ok);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return client;
    }
}
