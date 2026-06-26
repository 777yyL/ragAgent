package com.company.knowledge.search.service;

import com.company.knowledge.integration.ragflow.RetrievalApi;
import com.company.knowledge.permission.service.PermissionIndexService;
import com.company.knowledge.search.dto.SearchQuery;
import com.company.knowledge.search.dto.SearchResult;
import com.company.knowledge.search.dto.SearchResultChunk;
import com.company.knowledge.search.entity.SearchLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SearchService} 单元测试。
 *
 * <p>RetrievalApi / PermissionIndexService / SearchLogService 均 mock，
 * 验证 metadata_condition 注入、响应解析、埋点逻辑。
 */
class SearchServiceTest {

    private RetrievalApi retrievalApi;
    private PermissionIndexService permissionIndexService;
    private SearchLogService searchLogService;
    private SearchService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        retrievalApi = Mockito.mock(RetrievalApi.class);
        permissionIndexService = Mockito.mock(PermissionIndexService.class);
        searchLogService = Mockito.mock(SearchLogService.class);
        service = new SearchService(retrievalApi, permissionIndexService, searchLogService);
    }

    /**
     * 验证 USER 检索会注入权限元数据条件 + 解析响应 + 异步埋点。
     */
    @Test
    @SuppressWarnings("unchecked")
    void doSearch_userType_injectsMetadataCondition_andParsesChunks() throws Exception {
        // given
        SearchQuery q = new SearchQuery();
        q.setQuestion("发电机运行规程");
        q.setDatasetIds(Arrays.asList("ds-1"));
        q.setTopK(5);
        q.setSimilarityThreshold(0.3);

        Map<String, Object> mc = new LinkedHashMap<>();
        mc.put("logic", "and");
        List<Map<String, Object>> conds = new ArrayList<>();
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("field", "security_level");
        c1.put("comparison_operator", "le");
        c1.put("value", 2);
        conds.add(c1);
        mc.put("conditions", conds);

        when(permissionIndexService.buildMetadataCondition("u1")).thenReturn(mc);

        JsonNode fakeResp = mapper.readTree(
                "{\"code\":0,\"data\":{\"chunks\":[" +
                        "{\"id\":\"chk-1\",\"content\":\"正文A\",\"similarity\":0.95," +
                        "\"document_id\":\"doc-1\",\"document_keyword\":\"规程.docx\"," +
                        "\"kb_id\":\"ds-1\",\"positions\":[\"p1\"]}" +
                        "]}}");
        when(retrievalApi.retrieve(any(Map.class))).thenReturn(fakeResp);

        // when
        SearchResult result = service.doSearch(q, "u1", "USER");

        // then - 结果解析
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getChunks().size());
        SearchResultChunk chunk = result.getChunks().get(0);
        assertEquals("chk-1", chunk.getChunkId());
        assertEquals("正文A", chunk.getContent());
        assertEquals(0.95, chunk.getSimilarity(), 0.0001);
        assertEquals("doc-1", chunk.getDocumentId());
        assertEquals("规程.docx", chunk.getDocumentName());
        assertEquals("ds-1", chunk.getDatasetId());
        assertTrue(result.getResponseMs() >= 0);

        // then - 请求体包含 metadata_condition
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(retrievalApi).retrieve(bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        assertEquals("发电机运行规程", body.get("question"));
        assertEquals(Arrays.asList("ds-1"), body.get("dataset_ids"));
        assertEquals(5, body.get("top_k"));
        assertEquals(0.3, body.get("similarity_threshold"));
        assertNotNull(body.get("metadata_condition"));
        assertSame(mc, body.get("metadata_condition"));

        // then - 异步埋点（record 被调用）
        ArgumentCaptor<SearchLog> logCaptor = ArgumentCaptor.forClass(SearchLog.class);
        verify(searchLogService).record(logCaptor.capture());
        SearchLog logged = logCaptor.getValue();
        assertEquals("u1", logged.getUserId());
        assertEquals("发电机运行规程", logged.getQuery());
        assertEquals(1, logged.getResultCount().intValue());
        assertEquals("USER", logged.getType());
        assertArrayEquals(new String[]{"chk-1"}, logged.getTopChunkIds());
    }

    /**
     * 验证 TEST 类型不注入权限元数据条件。
     */
    @Test
    @SuppressWarnings("unchecked")
    void doSearch_testType_skipsMetadataCondition() throws Exception {
        SearchQuery q = new SearchQuery();
        q.setQuestion("test query");

        JsonNode fakeResp = mapper.readTree("{\"code\":0,\"data\":{\"chunks\":[]}}");
        when(retrievalApi.retrieve(any(Map.class))).thenReturn(fakeResp);

        SearchResult result = service.doSearch(q, "admin-1", "TEST");

        assertEquals(0, result.getTotal());
        assertTrue(result.getChunks().isEmpty());

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(retrievalApi).retrieve(bodyCaptor.capture());
        assertNull(bodyCaptor.getValue().get("metadata_condition"));

        // permissionIndexService 不应被调用
        verify(permissionIndexService, never()).buildMetadataCondition(any());

        // 埋点 type=TEST
        ArgumentCaptor<SearchLog> logCaptor = ArgumentCaptor.forClass(SearchLog.class);
        verify(searchLogService).record(logCaptor.capture());
        assertEquals("TEST", logCaptor.getValue().getType());
    }

    /**
     * 验证空 metadata_condition（conditions 为空）不会被塞进请求体。
     */
    @Test
    @SuppressWarnings("unchecked")
    void doSearch_emptyMetadataCondition_notIncluded() throws Exception {
        SearchQuery q = new SearchQuery();
        q.setQuestion("q");

        // 空条件（管理员场景）
        Map<String, Object> mc = new LinkedHashMap<>();
        mc.put("logic", "and");
        mc.put("conditions", new ArrayList<>());
        when(permissionIndexService.buildMetadataCondition("admin")).thenReturn(mc);

        JsonNode fakeResp = mapper.readTree("{\"code\":0,\"data\":{\"chunks\":[]}}");
        when(retrievalApi.retrieve(any(Map.class))).thenReturn(fakeResp);

        service.doSearch(q, "admin", "USER");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(retrievalApi).retrieve(bodyCaptor.capture());
        assertNull(bodyCaptor.getValue().get("metadata_condition"),
                "空 metadata_condition 不应注入");
    }

    /**
     * 验证 null 响应返回空结果。
     */
    @Test
    void doSearch_nullResponse_returnsEmpty() {
        SearchQuery q = new SearchQuery();
        q.setQuestion("q");
        when(retrievalApi.retrieve(any())).thenReturn(null);

        SearchResult result = service.doSearch(q, "u", "USER");
        assertEquals(0, result.getTotal());
        assertTrue(result.getChunks().isEmpty());
    }

    /**
     * 验证埋点异常不影响主流程。
     */
    @Test
    void doSearch_recordLogFailure_doesNotPropagate() throws Exception {
        SearchQuery q = new SearchQuery();
        q.setQuestion("q");

        JsonNode fakeResp = mapper.readTree("{\"code\":0,\"data\":{\"chunks\":[]}}");
        when(retrievalApi.retrieve(any())).thenReturn(fakeResp);
        // searchLogService.record 默认 void，即便内部抛异常也不应影响 SearchService
        // 这里只是验证调用不抛
        doThrow(new RuntimeException("db down")).when(searchLogService).record(any());

        // 不抛异常
        SearchResult result = service.doSearch(q, "u", "USER");
        assertNotNull(result);
    }
}
