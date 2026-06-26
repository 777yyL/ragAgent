package com.company.knowledge.agent;

import com.company.knowledge.agent.dto.*;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.integration.ragflow.ChatApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatService 单元测试。
 *
 * <p>重点验证 {@link ChatService#converse} 对 reference chunks 的解析（溯源核心）。
 */
class ChatServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ========================= Assistant =========================

    @Test
    void listAssistants_delegates() {
        ChatApi api = mock(ChatApi.class);
        JsonNode ok = mapper.valueToTree(Map.of(
                "code", 0,
                "data", Arrays.asList(
                        Map.of("id", "a1", "name", "n1"),
                        Map.of("id", "a2", "name", "n2"))));
        when(api.listChatAssistants()).thenReturn(ok);

        ChatService service = new ChatService(api, mapper);
        List<Map<String, Object>> result = service.listAssistants();
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(api).listChatAssistants();
    }

    @Test
    void createAssistant_mapsToBody() {
        ChatApi api = mock(ChatApi.class);
        when(api.createChatAssistant(anyMap())).thenReturn(mapper.valueToTree(Map.of("code", 0)));

        CreateChatAssistantRequest req = new CreateChatAssistantRequest();
        req.setName("a1");
        req.setDatasetIds(Arrays.asList("d1", "d2"));
        CreateChatAssistantRequest.Llm llm = new CreateChatAssistantRequest.Llm();
        llm.setModel("qwen-plus@Tongyi-Qianwen");
        llm.setTemperature(0.3);
        llm.setMaxTokens(2048);
        req.setLlm(llm);
        Map<String, Object> prompt = new HashMap<>();
        prompt.put("system", "你是助手");
        req.setPrompt(prompt);
        req.setIcon("icon-b64");

        ChatService service = new ChatService(api, mapper);
        service.createAssistant(req);

        verify(api).createChatAssistant(argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return "a1".equals(m.get("name"))
                    && Arrays.asList("d1", "d2").equals(m.get("dataset_ids"))
                    && "icon-b64".equals(m.get("icon"))
                    && "qwen-plus@Tongyi-Qianwen".equals(m.get("llm_id"))
                    && ((Map<?, ?>) m.get("llm_setting")).get("temperature").equals(0.3)
                    && ((Map<?, ?>) m.get("llm_setting")).get("max_tokens").equals(2048)
                    && prompt.equals(m.get("prompt_config"));
        }));
    }

    @Test
    void createAssistant_requiresName() {
        ChatApi api = mock(ChatApi.class);
        ChatService service = new ChatService(api, mapper);
        CreateChatAssistantRequest req = new CreateChatAssistantRequest();
        assertThrows(BizException.class, () -> service.createAssistant(req));
        verifyNoInteractions(api);
    }

    @Test
    void deleteAssistant_requiresId() {
        ChatApi api = mock(ChatApi.class);
        ChatService service = new ChatService(api, mapper);
        assertThrows(BizException.class, () -> service.deleteAssistant(null));
        assertThrows(BizException.class, () -> service.deleteAssistant(""));
        verifyNoInteractions(api);
    }

    @Test
    void deleteAssistant_delegates() {
        ChatApi api = mock(ChatApi.class);
        when(api.deleteChatAssistant("c1")).thenReturn(mapper.valueToTree(Map.of("code", 0)));
        ChatService service = new ChatService(api, mapper);
        service.deleteAssistant("c1");
        verify(api).deleteChatAssistant("c1");
    }

    // ========================= Session =========================

    @Test
    void createSession_requiresChatId() {
        ChatApi api = mock(ChatApi.class);
        ChatService service = new ChatService(api, mapper);
        assertThrows(BizException.class, () -> service.createSession(null, "s"));
        assertThrows(BizException.class, () -> service.createSession("", "s"));
    }

    @Test
    void createSession_withName_putsItInBody() {
        ChatApi api = mock(ChatApi.class);
        when(api.createSession(anyString(), anyMap())).thenReturn(mapper.valueToTree(Map.of("code", 0)));
        ChatService service = new ChatService(api, mapper);
        service.createSession("c1", "session-name");

        verify(api).createSession(eq("c1"), argThat(b -> "session-name".equals(((Map<?, ?>) b).get("name"))));
    }

    @Test
    void createSession_nullName_sendsEmptyBody() {
        ChatApi api = mock(ChatApi.class);
        when(api.createSession(anyString(), anyMap())).thenReturn(mapper.valueToTree(Map.of("code", 0)));
        ChatService service = new ChatService(api, mapper);
        service.createSession("c1", null);

        verify(api).createSession(eq("c1"), argThat(b -> !((Map<?, ?>) b).containsKey("name")));
    }

    @Test
    void listSessions_delegates() {
        ChatApi api = mock(ChatApi.class);
        JsonNode ok = mapper.valueToTree(Map.of(
                "code", 0,
                "data", Arrays.asList(
                        Map.of("id", "s1", "name", "sess-1"),
                        Map.of("id", "s2", "name", "sess-2"))));
        when(api.listSessions("c1")).thenReturn(ok);
        ChatService service = new ChatService(api, mapper);
        List<Map<String, Object>> result = service.listSessions("c1");
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(api).listSessions("c1");
    }

    @Test
    void deleteSession_requiresAllIds() {
        ChatApi api = mock(ChatApi.class);
        ChatService service = new ChatService(api, mapper);
        assertThrows(BizException.class, () -> service.deleteSession(null, "s"));
        assertThrows(BizException.class, () -> service.deleteSession("c", null));
        assertThrows(BizException.class, () -> service.deleteSession("c", ""));
    }

    // ========================= Converse (溯源核心) =========================

    @Test
    void converse_requiresChatId() {
        ChatApi api = mock(ChatApi.class);
        ChatService service = new ChatService(api, mapper);
        ConverseRequest req = new ConverseRequest();
        req.setQuestion("hi");
        assertThrows(BizException.class, () -> service.converse(null, req));
        verifyNoInteractions(api);
    }

    @Test
    void converse_requiresQuestion() {
        ChatApi api = mock(ChatApi.class);
        ChatService service = new ChatService(api, mapper);
        ConverseRequest req = new ConverseRequest();
        assertThrows(BizException.class, () -> service.converse("c1", req));
        // question=null
        req.setQuestion("");
        assertThrows(BizException.class, () -> service.converse("c1", req));
        verifyNoInteractions(api);
    }

    @Test
    void converse_parsesReferenceChunks() throws Exception {
        ChatApi api = mock(ChatApi.class);
        String raw = "{\n"
                + "  \"code\": 0,\n"
                + "  \"data\": {\n"
                + "    \"answer\": \"Hello ##0$$\",\n"
                + "    \"reference\": {\n"
                + "      \"total\": 1,\n"
                + "      \"chunks\": [\n"
                + "        {\n"
                + "          \"id\": \"chunk-1\",\n"
                + "          \"content\": \"sample content\",\n"
                + "          \"document_id\": \"doc-1\",\n"
                + "          \"document_name\": \"1.txt\",\n"
                + "          \"dataset_id\": \"ds-1\",\n"
                + "          \"similarity\": 0.82,\n"
                + "          \"positions\": [\"p1\"]\n"
                + "        }\n"
                + "      ],\n"
                + "      \"doc_aggs\": [{\"doc_name\":\"1.txt\",\"count\":1}]\n"
                + "    },\n"
                + "    \"id\": \"msg-1\",\n"
                + "    \"session_id\": \"sess-1\"\n"
                + "  }\n"
                + "}";
        when(api.converse(anyString(), any(), anyMap())).thenReturn(mapper.readTree(raw));

        ConverseRequest req = new ConverseRequest();
        req.setQuestion("what?");
        req.setSessionId("sess-1");
        ChatService service = new ChatService(api, mapper);

        ConverseResponse resp = service.converse("c1", req);

        assertEquals("Hello ##0$$", resp.getAnswer());
        assertEquals("msg-1", resp.getId());
        assertEquals("sess-1", resp.getSessionId());
        assertNotNull(resp.getReference());
        assertEquals(Integer.valueOf(1), resp.getReference().getTotal());
        assertEquals(1, resp.getReference().getChunks().size());

        ReferenceChunk chunk = resp.getReference().getChunks().get(0);
        assertEquals("chunk-1", chunk.getChunkId());
        assertEquals("sample content", chunk.getContent());
        assertEquals("doc-1", chunk.getDocumentId());
        assertEquals("1.txt", chunk.getDocumentName());
        assertEquals("ds-1", chunk.getDatasetId());
        assertEquals(0.82, chunk.getSimilarity(), 0.001);
        assertEquals(Collections.singletonList("p1"), chunk.getPositions());
    }

    @Test
    void converse_passesDatasetIdsWhenProvided() {
        ChatApi api = mock(ChatApi.class);
        when(api.converse(anyString(), any(), anyMap()))
                .thenReturn(mapper.valueToTree(Map.of("code", 0, "data", Map.of("answer", "ok"))));

        ConverseRequest req = new ConverseRequest();
        req.setQuestion("q");
        req.setDatasetIds(Arrays.asList("d1", "d2"));
        ChatService service = new ChatService(api, mapper);
        service.converse("c1", req);

        verify(api).converse(eq("c1"), any(), argThat(b -> {
            Map<String, Object> m = (Map<String, Object>) b;
            return Arrays.asList("d1", "d2").equals(m.get("dataset_ids"))
                    && "q".equals(m.get("question"));
        }));
    }

    @Test
    void converse_emptyReference_returnsEmptyChunks() throws Exception {
        ChatApi api = mock(ChatApi.class);
        String raw = "{\"code\":0,\"data\":{\"answer\":\"hi\",\"reference\":{}}}";
        when(api.converse(anyString(), any(), anyMap())).thenReturn(mapper.readTree(raw));

        ConverseRequest req = new ConverseRequest();
        req.setQuestion("q");
        ChatService service = new ChatService(api, mapper);
        ConverseResponse resp = service.converse("c1", req);

        assertEquals("hi", resp.getAnswer());
        assertNotNull(resp.getReference());
        assertEquals(0, resp.getReference().getChunks().size());
    }

    @Test
    void converse_missingReference_returnsEmptyChunks() throws Exception {
        ChatApi api = mock(ChatApi.class);
        // answer 在顶层（兼容非 data 嵌套的情况）
        String raw = "{\"code\":0,\"answer\":\"top\"}";
        when(api.converse(anyString(), any(), anyMap())).thenReturn(mapper.readTree(raw));

        ConverseRequest req = new ConverseRequest();
        req.setQuestion("q");
        ChatService service = new ChatService(api, mapper);
        ConverseResponse resp = service.converse("c1", req);

        assertEquals("top", resp.getAnswer());
        assertNotNull(resp.getReference());
        assertEquals(0, resp.getReference().getChunks().size());
    }

    @Test
    void converse_nullResponse_throws() {
        ChatApi api = mock(ChatApi.class);
        when(api.converse(anyString(), any(), anyMap())).thenReturn(null);

        ConverseRequest req = new ConverseRequest();
        req.setQuestion("q");
        ChatService service = new ChatService(api, mapper);
        BizException ex = assertThrows(BizException.class, () -> service.converse("c1", req));
        assertEquals(5003, ex.getCode());
    }

    @Test
    void converse_multipleChunks_allParsed() throws Exception {
        ChatApi api = mock(ChatApi.class);
        String raw = "{\"code\":0,\"data\":{\"answer\":\"a\",\"reference\":{"
                + "\"chunks\":["
                + "{\"id\":\"c1\",\"content\":\"x\",\"document_name\":\"a.txt\",\"similarity\":0.7},"
                + "{\"id\":\"c2\",\"content\":\"y\",\"document_name\":\"b.txt\",\"similarity\":0.9}"
                + "]}}}";
        when(api.converse(anyString(), any(), anyMap())).thenReturn(mapper.readTree(raw));

        ConverseRequest req = new ConverseRequest();
        req.setQuestion("q");
        ChatService service = new ChatService(api, mapper);
        ConverseResponse resp = service.converse("c1", req);

        List<ReferenceChunk> chunks = resp.getReference().getChunks();
        assertEquals(2, chunks.size());
        assertEquals("c1", chunks.get(0).getChunkId());
        assertEquals(0.7, chunks.get(0).getSimilarity(), 0.001);
        assertEquals("c2", chunks.get(1).getChunkId());
        assertEquals(0.9, chunks.get(1).getSimilarity(), 0.001);
    }

    // ========================= Feedback =========================

    @Test
    void updateFeedback_requiresAllIds() {
        ChatApi api = mock(ChatApi.class);
        ChatService service = new ChatService(api, mapper);
        assertThrows(BizException.class, () ->
                service.updateFeedback(null, "s", "m", new HashMap<>()));
        assertThrows(BizException.class, () ->
                service.updateFeedback("c", null, "m", new HashMap<>()));
        assertThrows(BizException.class, () ->
                service.updateFeedback("c", "s", null, new HashMap<>()));
    }

    @Test
    void updateFeedback_delegates() {
        ChatApi api = mock(ChatApi.class);
        when(api.updateMessageFeedback(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(mapper.valueToTree(Map.of("code", 0)));

        Map<String, Object> fb = new HashMap<>();
        fb.put("thumbup", true);
        ChatService service = new ChatService(api, mapper);
        service.updateFeedback("c1", "s1", "m1", fb);

        verify(api).updateMessageFeedback("c1", "s1", "m1", fb);
    }

    @Test
    void updateFeedback_nullFeedbackDefaultsToEmpty() {
        ChatApi api = mock(ChatApi.class);
        when(api.updateMessageFeedback(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(mapper.valueToTree(Map.of("code", 0)));

        ChatService service = new ChatService(api, mapper);
        service.updateFeedback("c1", "s1", "m1", null);

        verify(api).updateMessageFeedback(eq("c1"), eq("s1"), eq("m1"), anyMap());
    }
}
