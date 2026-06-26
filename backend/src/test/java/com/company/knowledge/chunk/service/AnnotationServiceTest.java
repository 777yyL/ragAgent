package com.company.knowledge.chunk.service;

import com.company.knowledge.chunk.dto.AnnotationUpdateRequest;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.integration.ragflow.ChunkApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AnnotationService} 单元测试。
 */
class AnnotationServiceTest {

    private ChunkApi chunkApi;
    private AnnotationService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        chunkApi = Mockito.mock(ChunkApi.class);
        service = new AnnotationService(chunkApi);
    }

    // ---- listPending ----

    @Test
    void listPending_filtersByAvailableAndKeywords() throws Exception {
        JsonNode resp = mapper.readTree("{\"data\":{\"chunks\":[" +
                // 1. available=false → pending
                "{\"id\":\"c1\",\"available\":false,\"important_keywords\":[\"k\"]}," +
                // 2. available=true + keywords 非空 → 已标注
                "{\"id\":\"c2\",\"available\":true,\"important_keywords\":[\"k1\"]}," +
                // 3. available=true + keywords 为空 → pending
                "{\"id\":\"c3\",\"available\":true,\"important_keywords\":[]}," +
                // 4. available=true + 无 keywords 字段 → pending
                "{\"id\":\"c4\",\"available\":true}," +
                // 5. available=true + keywords 全是空串 → pending
                "{\"id\":\"c5\",\"available\":true,\"important_keywords\":[\"\", \" \"]}" +
                "]}}");
        when(chunkApi.list(eq("ds"), eq("doc"), isNull(), eq(1), eq(30))).thenReturn(resp);

        List<Map<String, Object>> pending = service.listPending("ds", "doc", 1, 30);

        List<String> ids = collectIds(pending);
        assertTrue(ids.contains("c1"));
        assertFalse(ids.contains("c2"));
        assertTrue(ids.contains("c3"));
        assertTrue(ids.contains("c4"));
        assertTrue(ids.contains("c5"));
        assertEquals(4, pending.size());
    }

    @Test
    void listPending_missingDatasetId_throws() {
        assertThrows(BizException.class, () -> service.listPending(null, "doc", 1, 10));
        assertThrows(BizException.class, () -> service.listPending("", "doc", 1, 10));
    }

    @Test
    void listPending_missingDocId_throws() {
        assertThrows(BizException.class, () -> service.listPending("ds", null, 1, 10));
        assertThrows(BizException.class, () -> service.listPending("ds", "", 1, 10));
    }

    @Test
    void listPending_defaultPageAndPageSize() {
        JsonNode empty = mapper.createObjectNode();
        when(chunkApi.list(anyString(), anyString(), isNull(), eq(1), eq(30))).thenReturn(empty);
        service.listPending("ds", "doc", null, null);
        verify(chunkApi).list("ds", "doc", null, 1, 30);
    }

    @Test
    void listPending_pageSizeCappedAt200() {
        JsonNode empty = mapper.createObjectNode();
        when(chunkApi.list(anyString(), anyString(), isNull(), eq(1), eq(200))).thenReturn(empty);
        service.listPending("ds", "doc", 1, 1000);
        verify(chunkApi).list("ds", "doc", null, 1, 200);
    }

    @Test
    void listPending_nonArrayChunks_returnsEmpty() throws Exception {
        JsonNode resp = mapper.readTree("{\"data\":{}}");
        when(chunkApi.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(resp);
        assertTrue(service.listPending("ds", "doc", 1, 10).isEmpty());
    }

    // ---- getChunkDetail ----

    @Test
    void getChunkDetail_callsChunkApiGet() throws Exception {
        // ChunkApi 返回 RAGFlow 标准结构：{code:0, data:{...}}
        JsonNode fake = mapper.readTree("{\"code\":0,\"data\":{\"id\":\"c1\",\"content\":\"hello\"}}");
        when(chunkApi.get("ds", "doc", "c1")).thenReturn(fake);

        Map<String, Object> out = service.getChunkDetail("ds", "doc", "c1");
        // Service 走 extractData + toCamelCaseMap，返回 Map
        assertNotNull(out);
        assertEquals("c1", out.get("id"));
        assertEquals("hello", out.get("content"));
    }

    @Test
    void getChunkDetail_missingChunkId_throws() {
        assertThrows(BizException.class, () -> service.getChunkDetail("ds", "doc", null));
        assertThrows(BizException.class, () -> service.getChunkDetail("ds", "doc", ""));
    }

    // ---- updateAnnotation ----

    @Test
    @SuppressWarnings("unchecked")
    void updateAnnotation_allFields_buildsBody() {
        AnnotationUpdateRequest req = new AnnotationUpdateRequest();
        req.setImportantKeywords(Arrays.asList("k1", "k2"));
        req.setQuestions(Arrays.asList("什么是发电机?"));
        req.setTagKwd(Arrays.asList("规程"));
        req.setContent("修正后的正文");
        when(chunkApi.update(any(), any(), any(), any())).thenReturn(mapper.createObjectNode());

        service.updateAnnotation("ds", "doc", "c1", req);

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(chunkApi).update(eq("ds"), eq("doc"), eq("c1"), bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        assertEquals(Arrays.asList("k1", "k2"), body.get("important_keywords"));
        assertEquals(Arrays.asList("什么是发电机?"), body.get("questions"));
        assertEquals(Arrays.asList("规程"), body.get("tag_kwd"));
        assertEquals("修正后的正文", body.get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateAnnotation_partialFields_onlyNonNullableIncluded() {
        AnnotationUpdateRequest req = new AnnotationUpdateRequest();
        req.setImportantKeywords(Arrays.asList("k1"));
        // questions/tagKwd/content 保持 null

        service.updateAnnotation("ds", "doc", "c1", req);

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(chunkApi).update(any(), any(), any(), bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        assertEquals(1, body.size());
        assertTrue(body.containsKey("important_keywords"));
        assertFalse(body.containsKey("questions"));
        assertFalse(body.containsKey("tag_kwd"));
        assertFalse(body.containsKey("content"));
    }

    @Test
    void updateAnnotation_emptyRequest_throws() {
        AnnotationUpdateRequest req = new AnnotationUpdateRequest();
        // 全 null
        assertThrows(BizException.class, () -> service.updateAnnotation("ds", "doc", "c1", req));
    }

    @Test
    void updateAnnotation_nullRequest_throws() {
        assertThrows(BizException.class, () -> service.updateAnnotation("ds", "doc", "c1", null));
    }

    @Test
    void updateAnnotation_emptyKeywordsList_isWritten() {
        // important_keywords = [] 是显式清空，应当写入
        AnnotationUpdateRequest req = new AnnotationUpdateRequest();
        req.setImportantKeywords(Collections.emptyList());

        service.updateAnnotation("ds", "doc", "c1", req);

        verify(chunkApi).update(eq("ds"), eq("doc"), eq("c1"), any());
    }

    @Test
    void updateAnnotation_missingIds_throws() {
        AnnotationUpdateRequest req = new AnnotationUpdateRequest();
        req.setContent("x");
        assertThrows(BizException.class, () -> service.updateAnnotation(null, "doc", "c1", req));
        assertThrows(BizException.class, () -> service.updateAnnotation("ds", "", "c1", req));
        assertThrows(BizException.class, () -> service.updateAnnotation("ds", "doc", "", req));
    }

    // ---- markAnnotated ----

    @Test
    @SuppressWarnings("unchecked")
    void markAnnotated_setsAvailableTrue() {
        service.markAnnotated("ds", "doc", "c1");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(chunkApi).update(eq("ds"), eq("doc"), eq("c1"), bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        assertEquals(Boolean.TRUE, body.get("available"));
    }

    @Test
    void markAnnotated_missingIds_throws() {
        assertThrows(BizException.class, () -> service.markAnnotated(null, "doc", "c1"));
        assertThrows(BizException.class, () -> service.markAnnotated("ds", null, "c1"));
        assertThrows(BizException.class, () -> service.markAnnotated("ds", "doc", null));
    }

    // ---- helper ----

    private List<String> collectIds(List<Map<String, Object>> nodes) {
        List<String> ids = new java.util.ArrayList<>();
        for (Map<String, Object> n : nodes) {
            Object id = n.get("id");
            ids.add(id == null ? null : String.valueOf(id));
        }
        return ids;
    }
}
