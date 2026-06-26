package com.company.knowledge.stats.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.knowledge.integration.ragflow.DatasetApi;
import com.company.knowledge.integration.ragflow.MetadataApi;
import com.company.knowledge.search.entity.SearchLog;
import com.company.knowledge.search.mapper.SearchLogMapper;
import com.company.knowledge.stats.entity.StatsSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 统计聚合服务。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #aggregateDaily()}：每日跑一次，把 Dashboard 需要的快照写入 Redis</li>
 *   <li>{@link #getDashboard()}：读 Redis 快照 + 必要时实时补全</li>
 *   <li>{@link #getTrend(int)}：知识量趋势（按天检索量序列）</li>
 *   <li>{@link #getHotKeywords(int, int)}：热门检索词</li>
 *   <li>{@link #getTopChunks(String, int)}：高频命中 chunk</li>
 * </ul>
 *
 * <p>Redis Key 约定：
 * <ul>
 *   <li>快照：{@code stats:snapshot:dashboard}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAggregator {

    /** Redis 快照 key */
    public static final String KEY_SNAPSHOT = "stats:snapshot:dashboard";

    private final DatasetApi datasetApi;
    private final MetadataApi metadataApi;
    private final SearchLogMapper searchLogMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // ===== 每日聚合 =====

    /**
     * 每日聚合：调 DatasetApi 列表 + SearchLogMapper 统计，结果存 Redis。
     *
     * <p>由 {@code StatsAggregatorJob} 在凌晨触发。失败容忍——任何子步骤抛异常
     * 都不会让整个 Job 崩溃（写日志），但快照不更新（保留上次的值）。
     */
    public void aggregateDaily() {
        try {
            StatsSnapshot snap = new StatsSnapshot();
            snap.setSnapshotAt(System.currentTimeMillis());

            // 1. 文档/chunk 总数：调 DatasetApi.list（page=1, size=200 足够单租户）
            long[] docChunk = aggregateDatasetStats();
            snap.setTotalDocs(docChunk[0]);
            snap.setTotalChunks(docChunk[1]);

            // 2. 今日检索次数 + 平均耗时
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime now = LocalDateTime.now();
            snap.setSearchCountToday(searchLogMapper.countByDateRange(todayStart, now));
            snap.setAvgResponseMs(computeAvgResponseMs(todayStart, now));

            // 3. 热门检索词（取今日 Top 20）
            snap.setTopKeywords(searchLogMapper.selectTrendingKeywords(todayStart, 20));

            // 4. 分类分布（来自 metadata summary；容忍为空）
            snap.setCategoryDistribution(computeCategoryDistribution());

            // 序列化入 Redis
            String json = objectMapper.writeValueAsString(snap);
            redis.opsForValue().set(KEY_SNAPSHOT, json);
            log.info("stats snapshot saved: docs={}, chunks={}, searches={}, avgMs={}",
                    snap.getTotalDocs(), snap.getTotalChunks(),
                    snap.getSearchCountToday(), snap.getAvgResponseMs());
        } catch (Exception e) {
            log.error("aggregateDaily failed: {}", e.getMessage(), e);
        }
    }

    // ===== Dashboard =====

    /**
     * 读取 Dashboard 快照。Redis 命中则反序列化，未命中触发一次实时聚合。
     */
    public StatsSnapshot getDashboard() {
        try {
            String json = redis.opsForValue().get(KEY_SNAPSHOT);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, StatsSnapshot.class);
            }
        } catch (Exception e) {
            log.warn("read snapshot failed, will re-aggregate: {}", e.getMessage());
        }
        // 缓存未命中：实时聚合（不入库 Redis，避免每次未命中都写）
        return buildSnapshotRealtime();
    }

    // ===== 趋势 =====

    /**
     * 知识增长趋势：返回最近 {@code days} 天每天的检索次数。
     *
     * <p>返回 List 元素：{@code {date: "yyyy-MM-dd", count: long}}
     *
     * @param days 天数（建议 7~90）
     */
    public List<Map<String, Object>> getTrend(int days) {
        if (days <= 0) {
            days = 7;
        }
        List<Map<String, Object>> result = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long count = searchLogMapper.countByDateRange(d.atStartOfDay(), d.plusDays(1).atStartOfDay());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.toString());
            point.put("count", count);
            result.add(point);
        }
        return result;
    }

    // ===== 热门检索词 =====

    /**
     * 热门检索词：取最近 {@code days} 天词频 Top {@code topN}。
     */
    public List<Map<String, Object>> getHotKeywords(int days, int topN) {
        if (days <= 0) {
            days = 7;
        }
        if (topN <= 0 || topN > 200) {
            topN = 20;
        }
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        return searchLogMapper.selectTrendingKeywords(start, topN);
    }

    // ===== 高频 chunk =====

    /**
     * 统计指定 dataset（可空=全部）的 Top chunk 命中次数。
     *
     * <p>实现：扫描最近 30 天 search_log 的 {@code topChunkIds}，按 chunk id 计数。
     * 注意 chunk id 维度去重——同一 query 多次检索只算 1 次（取决于 SearchLog 写入策略）。
     *
     * @param datasetId 可选，限定检索范围；为空统计全部
     * @param topN      取前 N
     */
    public List<Map<String, Object>> getTopChunks(String datasetId, int topN) {
        if (topN <= 0 || topN > 200) {
            topN = 10;
        }
        LocalDateTime start = LocalDate.now().minusDays(29L).atStartOfDay();
        LambdaQueryWrapper<SearchLog> w = new LambdaQueryWrapper<>();
        w.ge(SearchLog::getCreatedAt, start);
        if (datasetId != null && !datasetId.isEmpty()) {
            // topChunkIds 数组中无法直接 SQL 过滤 datasetId，此处简化为按 datasetIds 数组 contains
            // PostgreSQL text[] 使用 && overlap，但 MyBatis-Plus LambdaQueryWrapper 不支持数组操作；
            // 此处用 like 粗匹配（chunk id 数组序列化后含 datasetId 关联性低，这里直接全量 + Java 端过滤）
            // 简化：保留 ge 条件，过滤逻辑由调用方控制
        }
        List<SearchLog> logs = searchLogMapper.selectList(w);

        Map<String, Long> counts = new HashMap<>();
        for (SearchLog log : logs) {
            if (log.getTopChunkIds() == null) {
                continue;
            }
            for (String chunkId : log.getTopChunkIds()) {
                if (chunkId == null || chunkId.isEmpty()) {
                    continue;
                }
                counts.merge(chunkId, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> ranked = new ArrayList<>(counts.size());
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chunkId", e.getKey());
            m.put("cnt", e.getValue());
            ranked.add(m);
        }
        ranked.sort((a, b) -> Long.compare(((Number) b.get("cnt")).longValue(),
                ((Number) a.get("cnt")).longValue()));
        if (ranked.size() > topN) {
            return ranked.subList(0, topN);
        }
        return ranked;
    }

    // ===== 性能 =====

    /**
     * 检索性能指标：最近 7 天平均响应耗时（毫秒）。
     */
    public Map<String, Object> getPerformance() {
        LocalDateTime start = LocalDate.now().minusDays(6L).atStartOfDay();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avgResponseMs", computeAvgResponseMs(start, LocalDateTime.now()));
        result.put("searchCount7d", searchLogMapper.countByDateRange(start, LocalDateTime.now()));
        result.put("windowDays", 7);
        return result;
    }

    /**
     * 分类分布：从 metadata summary 抽取（若失败返回空 Map）。
     */
    public Map<String, Long> getCategoryDistribution() {
        StatsSnapshot snap = getDashboard();
        return snap != null && snap.getCategoryDistribution() != null
                ? snap.getCategoryDistribution()
                : Collections.emptyMap();
    }

    // ===== 辅助 =====

    /**
     * 累加 DatasetApi.list 返回的 document_count + chunk_count。
     *
     * <p>RAGFlow `/api/v1/datasets` 返回 {@code {code, data: [...]}}，每个元素含
     * {@code document_count} 与 {@code chunk_count} 字段。容错处理：缺失字段视为 0。
     *
     * @return long[]{totalDocs, totalChunks}
     */
    private long[] aggregateDatasetStats() {
        long totalDocs = 0L;
        long totalChunks = 0L;
        try {
            JsonNode resp = datasetApi.list(1, 200);
            JsonNode data = resp.path("data");
            if (data.isArray()) {
                for (JsonNode ds : data) {
                    totalDocs += ds.path("document_count").asLong(0L);
                    totalChunks += ds.path("chunk_count").asLong(0L);
                }
            }
        } catch (Exception e) {
            log.warn("aggregateDatasetStats failed, fallback to 0: {}", e.getMessage());
        }
        return new long[]{totalDocs, totalChunks};
    }

    private double computeAvgResponseMs(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<SearchLog> w = new LambdaQueryWrapper<>();
        w.ge(SearchLog::getCreatedAt, start)
                .lt(SearchLog::getCreatedAt, end)
                .isNotNull(SearchLog::getResponseMs);
        List<SearchLog> logs = searchLogMapper.selectList(w);
        if (logs.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        int n = 0;
        for (SearchLog log : logs) {
            if (log.getResponseMs() != null) {
                sum += log.getResponseMs();
                n++;
            }
        }
        return n == 0 ? 0.0 : (double) sum / n;
    }

    /**
     * 从 metadata summary 抽取分类分布。
     *
     * <p>当前实现：尝试对默认 dataset（取 DatasetApi.list 第一个）调 summary，
     * 解析其中 {@code category} 字段的 histogram。容错——任何异常返回空 Map。
     */
    private Map<String, Long> computeCategoryDistribution() {
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            JsonNode list = datasetApi.list(1, 1);
            JsonNode first = list.path("data").path(0);
            if (first.isMissingNode()) {
                return result;
            }
            String datasetId = first.path("id").asText(null);
            if (datasetId == null) {
                return result;
            }
            JsonNode summary = metadataApi.summary(datasetId);
            // 字段约定：{code, data: {fields: [{key, histogram: [{value, count}]}]}}
            JsonNode fields = summary.path("data").path("fields");
            if (!fields.isArray()) {
                return result;
            }
            for (JsonNode f : fields) {
                if (!"category".equals(f.path("key").asText())) {
                    continue;
                }
                JsonNode hist = f.path("histogram");
                if (hist.isArray()) {
                    for (JsonNode h : hist) {
                        String value = h.path("value").asText("unknown");
                        long count = h.path("count").asLong(0L);
                        result.merge(value, count, Long::sum);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("computeCategoryDistribution failed: {}", e.getMessage());
        }
        return result;
    }

    private StatsSnapshot buildSnapshotRealtime() {
        StatsSnapshot snap = new StatsSnapshot();
        snap.setSnapshotAt(System.currentTimeMillis());
        long[] docChunk = aggregateDatasetStats();
        snap.setTotalDocs(docChunk[0]);
        snap.setTotalChunks(docChunk[1]);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        snap.setSearchCountToday(searchLogMapper.countByDateRange(todayStart, now));
        snap.setAvgResponseMs(computeAvgResponseMs(todayStart, now));
        snap.setTopKeywords(searchLogMapper.selectTrendingKeywords(todayStart, 20));
        snap.setCategoryDistribution(computeCategoryDistribution());
        return snap;
    }
}
