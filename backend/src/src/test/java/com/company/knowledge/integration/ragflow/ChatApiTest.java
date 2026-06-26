package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatApi 纯单元测试。
 *
 * <p>用 Mockito 验证 path/body 构造，不依赖 RAGFlow。集成测试见 {@link ChatApiIntegrationTest}。
 */
class ChatApiTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createChatAssistant_postsToChatsCollection() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "test-chat");
        api.createChatAssistant(body);

        verify(client).post(eq("/api/v1/chats"), same(body));
    }

    @Test
    void updateChat_putsToChatsIdPath() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "renamed");
        api.updateChat("c1", body);

        verify(client).put(eq("/api/v1/chats/c1"), same(body));
    }

    @Test
    void listChatAssistants_withAllParams_buildsQueryParams() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        api.listChatAssistants(2, 50, "kw", "idx", "namex");

        verify(client).get(eq("/api/v1/chats"), argThat(m -> {
            Map<String, Object> p = (Map<String, Object>) m;
            return Integer.valueOf(2).equals(p.get("page"))
                    && Integer.valueOf(50).equals(p.get("page_size"))
                    && "kw".equals(p.get("keywords"))
                    && "idx".equals(p.get("id"))
                    && "namex".equals(p.get("name"));
        }));
    }

    @Test
    void listChatAssistants_noParams_sendsEmptyParams() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        api.listChatAssistants();

        verify(client).get(eq("/api/v1/chats"), anyMap());
    }

    @Test
    void getChatAssistant_getsIdPath() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        api.getChatAssistant("c1");

        verify(client).get(eq("/api/v1/chats/c1"), isNull());
    }

    @Test
    void deleteChatAssistant_deletesIdPath() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        api.deleteChatAssistant("c1");

        verify(client).delete(eq("/api/v1/chats/c1"), isNull());
    }

    @Test
    void createSession_postsToSessionsPath() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "session-1");
        api.createSession("c1", body);

        verify(client).post(eq("/api/v1/chats/c1/sessions"), anyMap());
    }

    @Test
    void listSessions_usesSessionsPath() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        api.listSessions("c1", 2, 10);

        verify(client).get(eq("/api/v1/chats/c1/sessions"), anyMap());
    }

    @Test
    void deleteSession_wrapsIdInBody() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        api.deleteSession("c1", "s1");

        verify(client).deleteWithBody(eq("/api/v1/chats/c1/sessions"), argThat(b -> {
            @SuppressWarnings("unchecked")
            java.util.List<String> ids = (java.util.List<String>) ((Map<String, Object>) b).get("ids");
            return ids != null && ids.size() == 1 && "s1".equals(ids.get(0));
        }));
    }

    @Test
    void converse_injectsChatIdAndDefaultStream() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("question", "hello");
        api.converse("c1", "s1", body);

        verify(client).post(eq("/api/v1/chats/c1/completions"), argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return "c1".equals(m.get("chat_id"))
                    && "s1".equals(m.get("session_id"))
                    && Boolean.FALSE.equals(m.get("stream"))
                    && "hello".equals(m.get("question"));
        }));
    }

    @Test
    void converse_respectsCallerStreamChoice() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        Map<String, Object> body = new HashMap<>();
        body.put("question", "hi");
        body.put("stream", true);
        api.converse("c1", null, body);

        verify(client).post(eq("/api/v1/chats/c1/completions"), argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return Boolean.TRUE.equals(m.get("stream")) && !m.containsKey("session_id");
        }));
    }

    @Test
    void updateMessageFeedback_putsToFeedbackPath() {
        RAGFlowClient client = mockClient();
        ChatApi api = new ChatApi(client);

        Map<String, Object> fb = new HashMap<>();
        fb.put("thumbup", false);
        fb.put("feedback", "bad");
        api.updateMessageFeedback("c1", "s1", "m1", fb);

        verify(client).put(eq("/api/v1/chats/c1/sessions/s1/messages/m1/feedback"), same(fb));
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
