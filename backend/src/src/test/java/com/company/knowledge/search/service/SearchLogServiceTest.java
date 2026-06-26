package com.company.knowledge.search.service;

import com.company.knowledge.common.result.PageResult;
import com.company.knowledge.search.entity.SearchLog;
import com.company.knowledge.search.mapper.SearchLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link SearchLogService} 单元测试。
 *
 * <p>Mapper mock，验证统计方法参数传递 + 容错。
 */
class SearchLogServiceTest {

    private SearchLogMapper mapper;
    private SearchLogService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SearchLogMapper.class);
        service = new SearchLogService(mapper);
    }

    @Test
    void record_insertsLogEntry() {
        SearchLog log = new SearchLog();
        log.setUserId("u1");
        log.setQuery("q");
        when(mapper.insert(any())).thenReturn(1);

        service.record(log);

        verify(mapper).insert(log);
    }

    @Test
    void record_nullEntry_doesNothing() {
        service.record(null);
        verify(mapper, never()).insert(any());
    }

    @Test
    void record_dbFailure_doesNotPropagate() {
        SearchLog log = new SearchLog();
        log.setQuery("q");
        when(mapper.insert(any())).thenThrow(new RuntimeException("db down"));

        // 不抛
        service.record(log);
        verify(mapper).insert(log);
    }

    @Test
    void listTrendingKeywords_normal_returnsList() {
        List<Map<String, Object>> fake = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("keyword", "发电机");
        row.put("cnt", 42L);
        fake.add(row);
        when(mapper.selectTrendingKeywords(any(LocalDateTime.class), eq(10))).thenReturn(fake);

        List<Map<String, Object>> out = service.listTrendingKeywords(7, 10);

        assertEquals(1, out.size());
        assertEquals("发电机", out.get(0).get("keyword"));
    }

    @Test
    void listTrendingKeywords_invalidArgs_returnsEmpty() {
        assertTrue(service.listTrendingKeywords(0, 10).isEmpty());
        assertTrue(service.listTrendingKeywords(7, 0).isEmpty());
        assertTrue(service.listTrendingKeywords(-1, -1).isEmpty());
    }

    @Test
    void listTrendingKeywords_dbFailure_returnsEmpty() {
        when(mapper.selectTrendingKeywords(any(), anyInt()))
                .thenThrow(new RuntimeException("sql err"));
        assertTrue(service.listTrendingKeywords(7, 10).isEmpty());
    }

    @Test
    void listRecentByUser_normal_returnsList() {
        SearchLog l = new SearchLog();
        l.setUserId("u1");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(l));

        List<SearchLog> out = service.listRecentByUser("u1", 5);
        assertEquals(1, out.size());
    }

    @Test
    void listRecentByUser_invalidArgs_returnsEmpty() {
        assertTrue(service.listRecentByUser(null, 5).isEmpty());
        assertTrue(service.listRecentByUser("", 5).isEmpty());
        assertTrue(service.listRecentByUser("u1", 0).isEmpty());
    }

    @Test
    void listRecentByUser_dbFailure_returnsEmpty() {
        when(mapper.selectList(any())).thenThrow(new RuntimeException("err"));
        assertTrue(service.listRecentByUser("u1", 5).isEmpty());
    }

    @Test
    void countByDateRange_normal_returnsCount() {
        when(mapper.countByDateRange(any(), any())).thenReturn(100L);
        long c = service.countByDateRange(LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertEquals(100L, c);
    }

    @Test
    void countByDateRange_invalidArgs_returnsZero() {
        assertEquals(0L, service.countByDateRange(null, LocalDateTime.now()));
        assertEquals(0L, service.countByDateRange(LocalDateTime.now(), null));
        // end <= start
        LocalDateTime t = LocalDateTime.now();
        assertEquals(0L, service.countByDateRange(t, t));
        assertEquals(0L, service.countByDateRange(t.plusDays(1), t));
    }

    @Test
    void countByDateRange_dbFailure_returnsZero() {
        when(mapper.countByDateRange(any(), any())).thenThrow(new RuntimeException("err"));
        long c = service.countByDateRange(LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertEquals(0L, c);
    }

    @Test
    void listHistory_normal_returnsPagedResult() {
        SearchLog l = new SearchLog();
        l.setId(1L);
        when(mapper.selectCount(any())).thenReturn(50L);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(l));

        PageResult<SearchLog> result = service.listHistory(2, 10);

        assertEquals(50L, result.getTotal());
        assertEquals(2, result.getPageNo());
        assertEquals(10, result.getPageSize());
        assertEquals(1, result.getList().size());
    }

    @Test
    void listHistory_invalidArgs_usesDefaults() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.selectList(any())).thenReturn(new ArrayList<>());

        PageResult<SearchLog> r1 = service.listHistory(0, 0);
        assertEquals(1, r1.getPageNo());
        assertEquals(20, r1.getPageSize());

        PageResult<SearchLog> r2 = service.listHistory(-1, 500);
        assertEquals(1, r2.getPageNo());
        assertEquals(20, r2.getPageSize());
    }

    @Test
    void listHistory_dbFailure_returnsEmpty() {
        when(mapper.selectCount(any())).thenThrow(new RuntimeException("err"));
        PageResult<SearchLog> r = service.listHistory(1, 10);
        assertEquals(0L, r.getTotal());
        assertTrue(r.getList().isEmpty());
    }
}
