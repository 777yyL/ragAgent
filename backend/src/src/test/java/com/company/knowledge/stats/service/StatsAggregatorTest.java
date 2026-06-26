package com.company.knowledge.stats.service;

import com.company.knowledge.integration.ragflow.DatasetApi;
import com.company.knowledge.integration.ragflow.MetadataApi;
import com.company.knowledge.search.entity.SearchLog;
import com.company.knowledge.search.mapper.SearchLogMapper;
import com.company.knowledge.stats.entity.StatsSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link StatsAggregator} 单元测试。覆盖：
 * <ul>
 *   <li>aggregateDaily：聚合 DatasetApi + SearchLogMapper，写入 Redis</li>
 *   <li>getDashboard：缓存命中反序列化；缓存未命中实时聚合</li>
 *   <li>getTrend：按天返回序列</li>
 *   <li>getHotKeywords：透传 mapper</li>
 *   <li>getTopChunks：扫描 search_log 聚合 Top N</li>
 *   <li>aggregateDaily 容错：DatasetApi 异常不影响整体</li>
 * </ul>
 *
 * <p>所有外部依赖全部 mock。
 */
class StatsAggregatorTest {

    private DatasetApi datasetApi;
    private MetadataApi metadataApi;
    private SearchLogMapper searchLogMapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private StatsAggregator aggregator;

    @BeforeEach
    void setUp() {
        datasetApi = Mockito.mock(DatasetApi.class);
        metadataApi = Mockito.mock(MetadataApi.class);
        searchLogMapper = Mockito.mock(SearchLogMapper.class);
        redis = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        objectMapper = new ObjectMapper();
        aggregator = new StatsAggregator(datasetApi, metadataApi, searchLogMapper, redis, objectMapper);
    }

    // ===== aggregateDaily =====

    @Test
    void aggregateDaily_writesSnapshotToRedis() throws Exception {
        when(datasetApi.list(anyInt(), anyInt())).thenReturn(datasetListResponse(5L, 100L));
        when(searchLogMapper.countByDateRange(any(), any())).thenReturn(42L);
        when(searchLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(searchLogMapper.selectTrendingKeywords(any(), anyInt()))
                .thenReturn(Arrays.asList(keywordEntry("发电机", 10L)));
        when(metadataApi.summary(anyString())).thenReturn(summaryResponse());

        aggregator.aggregateDaily();

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCap.capture(), jsonCap.capture());
        assertEquals(StatsAggregator.KEY_SNAPSHOT, keyCap.getValue());

        StatsSnapshot snap = objectMapper.readValue(jsonCap.getValue(), StatsSnapshot.class);
        assertEquals(5L, snap.getTotalDocs(), "totalDocs 应=dataset 累加");
        assertEquals(100L, snap.getTotalChunks());
        assertEquals(42L, snap.getSearchCountToday());
        assertEquals(0.0, snap.getAvgResponseMs(), 0.001);
        assertEquals(1, snap.getTopKeywords().size());
        assertNotNull(snap.getCategoryDistribution());
        assertTrue(snap.getSnapshotAt() > 0);
    }

    @Test
    void aggregateDaily_datasetApiFails_doesNotThrowAndSkipsSnapshot() {
        when(datasetApi.list(anyInt(), anyInt())).thenThrow(new RuntimeException("ragflow down"));
        when(searchLogMapper.countByDateRange(any(), any())).thenReturn(0L);
        when(searchLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(searchLogMapper.selectTrendingKeywords(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(metadataApi.summary(anyString())).thenReturn(emptyObject());

        // DatasetApi 异常被内部 try/catch 吞掉（aggregateDatasetStats 容错），整体不抛
        aggregator.aggregateDaily();

        verify(valueOps).set(eq(StatsAggregator.KEY_SNAPSHOT), anyString());
    }

    // ===== getDashboard =====

    @Test
    void getDashboard_cacheHit_returnsDeserialized() throws Exception {
        StatsSnapshot cached = new StatsSnapshot();
        cached.setTotalDocs(7L);
        cached.setSearchCountToday(99L);
        cached.setSnapshotAt(123L);
        when(valueOps.get(StatsAggregator.KEY_SNAPSHOT)).thenReturn(objectMapper.writeValueAsString(cached));

        StatsSnapshot out = aggregator.getDashboard();

        assertNotNull(out);
        assertEquals(7L, out.getTotalDocs());
        assertEquals(99L, out.getSearchCountToday());
        // 未走实时聚合
        verify(datasetApi, never()).list(anyInt(), anyInt());
    }

    @Test
    void getDashboard_cacheMiss_fallsBackToRealtime() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(datasetApi.list(anyInt(), anyInt())).thenReturn(datasetListResponse(3L, 50L));
        when(searchLogMapper.countByDateRange(any(), any())).thenReturn(10L);
        when(searchLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(searchLogMapper.selectTrendingKeywords(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(metadataApi.summary(anyString())).thenReturn(emptyObject());

        StatsSnapshot out = aggregator.getDashboard();

        assertNotNull(out);
        assertEquals(3L, out.getTotalDocs());
        assertEquals(10L, out.getSearchCountToday());
        // 缓存未命中不应写 Redis
        verify(valueOps, never()).set(anyString(), anyString());
    }

    // ===== getTrend =====

    @Test
    void getTrend_returnsOnePointPerDay() {
        when(searchLogMapper.countByDateRange(any(), any())).thenReturn(5L);

        List<Map<String, Object>> trend = aggregator.getTrend(3);

        assertEquals(3, trend.size());
        for (int i = 0; i < trend.size(); i++) {
            assertEquals(5L, ((Number) trend.get(i).get("count")).longValue());
            assertNotNull(trend.get(i).get("date"));
        }
        verify(searchLogMapper, times(3)).countByDateRange(any(), any());
    }

    @Test
    void getTrend_invalidDays_defaultsTo7() {
        when(searchLogMapper.countByDateRange(any(), any())).thenReturn(0L);
        assertEquals(7, aggregator.getTrend(0).size());
        assertEquals(7, aggregator.getTrend(-1).size());
    }

    // ===== getHotKeywords =====

    @Test
    void getHotKeywords_passesThroughMapperResult() {
        List<Map<String, Object>> expected = Arrays.asList(keywordEntry("汽轮机", 5L));
        when(searchLogMapper.selectTrendingKeywords(any(), anyInt())).thenReturn(expected);

        List<Map<String, Object>> out = aggregator.getHotKeywords(7, 10);
        assertSame(expected, out);
    }

    @Test
    void getHotKeywords_clampsTopN() {
        when(searchLogMapper.selectTrendingKeywords(any(), eq(20))).thenReturn(Collections.emptyList());
        aggregator.getHotKeywords(7, 0);     // topN=0 -> 默认 20
        aggregator.getHotKeywords(7, 9999);  // topN 过大 -> 20
        verify(searchLogMapper, times(2)).selectTrendingKeywords(any(), eq(20));
    }

    // ===== getTopChunks =====

    @Test
    void getTopChunks_aggregatesAndRanks() {
        SearchLog log1 = searchLogWithChunks("c1", "c2", "c3");
        SearchLog log2 = searchLogWithChunks("c1", "c1", "c2");
        when(searchLogMapper.selectList(any())).thenReturn(Arrays.asList(log1, log2));

        List<Map<String, Object>> out = aggregator.getTopChunks(null, 2);

        assertEquals(2, out.size());
        // c1 出现 3 次（log1 1次 + log2 2次），c2 出现 2 次
        assertEquals("c1", out.get(0).get("chunkId"));
        assertEquals(3L, ((Number) out.get(0).get("cnt")).longValue());
        assertEquals("c2", out.get(1).get("chunkId"));
        assertEquals(2L, ((Number) out.get(1).get("cnt")).longValue());
    }

    @Test
    void getTopChunks_emptyLogs_returnsEmpty() {
        when(searchLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(aggregator.getTopChunks(null, 10).isEmpty());
    }

    @Test
    void getTopChunks_moreThanTopN_truncated() {
        SearchLog log = searchLogWithChunks("a", "b", "c", "d", "e");
        when(searchLogMapper.selectList(any())).thenReturn(Collections.singletonList(log));

        List<Map<String, Object>> out = aggregator.getTopChunks(null, 2);
        assertEquals(2, out.size());
    }

    // ===== getPerformance =====

    @Test
    void getPerformance_returnsAvgAndCount() {
        when(searchLogMapper.countByDateRange(any(), any())).thenReturn(100L);
        SearchLog slow = new SearchLog();
        slow.setResponseMs(200);
        SearchLog fast = new SearchLog();
        fast.setResponseMs(100);
        when(searchLogMapper.selectList(any())).thenReturn(Arrays.asList(slow, fast));

        Map<String, Object> out = aggregator.getPerformance();

        assertEquals(100L, ((Number) out.get("searchCount7d")).longValue());
        assertEquals(150.0, (double) out.get("avgResponseMs"), 0.001);
        assertEquals(7, ((Number) out.get("windowDays")).intValue());
    }

    // ===== 辅助 =====

    private Map<String, Object> keywordEntry(String kw, long cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("keyword", kw);
        m.put("cnt", cnt);
        return m;
    }

    private JsonNode datasetListResponse(long docCount, long chunkCount) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        root.put("code", 0);
        root.set("data", JsonNodeFactory.instance.arrayNode()
                .add(JsonNodeFactory.instance.objectNode()
                        .put("id", "ds-1")
                        .put("document_count", docCount)
                        .put("chunk_count", chunkCount)));
        return root;
    }

    private JsonNode summaryResponse() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        ObjectNode field = JsonNodeFactory.instance.objectNode();
        field.put("key", "category");
        field.set("histogram", JsonNodeFactory.instance.arrayNode()
                .add(JsonNodeFactory.instance.objectNode().put("value", "汽机").put("count", 10))
                .add(JsonNodeFactory.instance.objectNode().put("value", "锅炉").put("count", 5)));
        data.set("fields", JsonNodeFactory.instance.arrayNode().add(field));
        root.set("data", data);
        return root;
    }

    private JsonNode emptyObject() {
        return JsonNodeFactory.instance.objectNode();
    }

    private SearchLog searchLogWithChunks(String... chunkIds) {
        SearchLog log = new SearchLog();
        log.setTopChunkIds(chunkIds);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}
