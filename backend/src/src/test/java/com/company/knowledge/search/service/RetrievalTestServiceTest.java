package com.company.knowledge.search.service;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.common.result.PageResult;
import com.company.knowledge.search.dto.RetrievalTestRequest;
import com.company.knowledge.search.dto.SearchQuery;
import com.company.knowledge.search.dto.SearchResult;
import com.company.knowledge.search.entity.SearchLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RetrievalTestService} 单元测试。
 */
class RetrievalTestServiceTest {

    private SearchService searchService;
    private SearchLogService searchLogService;
    private RetrievalTestService service;

    @BeforeEach
    void setUp() {
        searchService = Mockito.mock(SearchService.class);
        searchLogService = Mockito.mock(SearchLogService.class);
        service = new RetrievalTestService(searchService, searchLogService);

        // 模拟登录用户
        UserContext.set(new UserContext.CurrentUser(
                "admin-1", "管理员", "org-1", "@root@集团@",
                new HashSet<>(Arrays.asList("ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void test_normal_callsDoSearchWithTestType() {
        RetrievalTestRequest req = new RetrievalTestRequest();
        req.setDatasetId("ds-1");
        req.setQuestion("test q");
        req.setTopK(8);
        req.setSimilarityThreshold(0.5);
        req.setVectorSimilarityWeight(0.7);
        req.setRerankId("rerank-1");
        req.setUseKg(true);
        req.setKeyword(false);
        req.setHighlight(false);

        SearchResult fake = new SearchResult();
        fake.setTotal(3);
        when(searchService.doSearch(any(SearchQuery.class), eq("admin-1"), eq("TEST")))
                .thenReturn(fake);

        SearchResult out = service.test(req);

        assertSame(fake, out);
        ArgumentCaptor<SearchQuery> queryCaptor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchService).doSearch(queryCaptor.capture(), eq("admin-1"), eq("TEST"));
        SearchQuery q = queryCaptor.getValue();
        assertEquals("test q", q.getQuestion());
        assertEquals(Collections.singletonList("ds-1"), q.getDatasetIds());
        assertEquals(8, q.getTopK().intValue());
        assertEquals(0.5, q.getSimilarityThreshold().doubleValue(), 0.0001);
        assertEquals(0.7, q.getVectorSimilarityWeight().doubleValue(), 0.0001);
        assertEquals("rerank-1", q.getRerankId());
        assertTrue(q.getUseKg());
        assertFalse(q.getKeyword());
        assertFalse(q.getHighlight());
    }

    @Test
    void test_defaultValuesApplied() {
        RetrievalTestRequest req = new RetrievalTestRequest();
        req.setDatasetId("ds-1");
        req.setQuestion("q");
        // topK/threshold/useKg/keyword/highlight 使用默认值

        when(searchService.doSearch(any(), anyString(), anyString())).thenReturn(new SearchResult());

        service.test(req);

        ArgumentCaptor<SearchQuery> queryCaptor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchService).doSearch(queryCaptor.capture(), anyString(), anyString());
        SearchQuery q = queryCaptor.getValue();
        assertEquals(10, q.getTopK().intValue());
        assertEquals(0.2, q.getSimilarityThreshold().doubleValue(), 0.0001);
        assertFalse(q.getUseKg());
        assertTrue(q.getKeyword());
        assertTrue(q.getHighlight());
    }

    @Test
    void test_nullRequest_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.test(null));
    }

    @Test
    void test_missingDatasetId_throws() {
        RetrievalTestRequest req = new RetrievalTestRequest();
        req.setQuestion("q");
        assertThrows(IllegalArgumentException.class, () -> service.test(req));
    }

    @Test
    void test_missingQuestion_throws() {
        RetrievalTestRequest req = new RetrievalTestRequest();
        req.setDatasetId("ds");
        assertThrows(IllegalArgumentException.class, () -> service.test(req));
    }

    @Test
    void test_emptyDatasetId_throws() {
        RetrievalTestRequest req = new RetrievalTestRequest();
        req.setDatasetId("");
        req.setQuestion("q");
        assertThrows(IllegalArgumentException.class, () -> service.test(req));
    }

    @Test
    void listHistory_filtersOnlyTestType() {
        SearchLog userLog = new SearchLog();
        userLog.setType("USER");
        userLog.setQuery("user query");
        SearchLog testLog1 = new SearchLog();
        testLog1.setType("TEST");
        testLog1.setQuery("test1");
        SearchLog testLog2 = new SearchLog();
        testLog2.setType("TEST");
        testLog2.setQuery("test2");

        PageResult<SearchLog> all = PageResult.of(3L, 1, 20,
                Arrays.asList(userLog, testLog1, testLog2));
        when(searchLogService.listHistory(1, 20)).thenReturn(all);

        PageResult<SearchLog> result = service.listHistory(1, 20);

        assertEquals(3L, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("TEST", result.getList().get(0).getType());
        assertEquals("TEST", result.getList().get(1).getType());
    }

    @Test
    void listHistory_noTestLogs_returnsEmptyList() {
        SearchLog userLog = new SearchLog();
        userLog.setType("USER");
        PageResult<SearchLog> all = PageResult.of(1L, 1, 20,
                new ArrayList<>(Collections.singletonList(userLog)));
        when(searchLogService.listHistory(anyInt(), anyInt())).thenReturn(all);

        PageResult<SearchLog> result = service.listHistory(1, 20);
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void listHistory_emptyResult_returnsEmpty() {
        when(searchLogService.listHistory(anyInt(), anyInt()))
                .thenReturn(PageResult.of(0L, 1, 20, Collections.emptyList()));

        PageResult<SearchLog> result = service.listHistory(1, 20);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }
}
