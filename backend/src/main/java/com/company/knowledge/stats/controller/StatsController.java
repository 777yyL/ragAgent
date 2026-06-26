package com.company.knowledge.stats.controller;

import com.company.knowledge.common.result.Result;
import com.company.knowledge.stats.entity.StatsSnapshot;
import com.company.knowledge.stats.service.StatsAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 统计分析 REST API。
 *
 * <p>路径：{@code /api/stats}。提供仪表盘、趋势、热门词、Top chunk、分类分布、性能等只读指标。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsAggregator aggregator;

    /**
     * 仪表盘快照（来自 Redis 缓存，未命中实时聚合）。
     */
    @GetMapping("/dashboard")
    public Result<StatsSnapshot> dashboard() {
        return Result.success(aggregator.getDashboard());
    }

    /**
     * 知识增长趋势（按天检索次数序列）。
     *
     * @param days 天数，默认 30
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        return Result.success(aggregator.getTrend(days));
    }

    /**
     * 热门检索词。
     *
     * @param days 时间窗（天），默认 7
     * @param topN 取前 N，默认 20
     */
    @GetMapping("/hot-keywords")
    public Result<List<Map<String, Object>>> hotKeywords(@RequestParam(defaultValue = "7") int days,
                                                          @RequestParam(defaultValue = "20") int topN) {
        return Result.success(aggregator.getHotKeywords(days, topN));
    }

    /**
     * 高频命中 chunk。
     *
     * @param datasetId 可选，按 dataset 过滤
     * @param topN      取前 N，默认 10
     */
    @GetMapping("/top-chunks")
    public Result<List<Map<String, Object>>> topChunks(@RequestParam(required = false) String datasetId,
                                                        @RequestParam(defaultValue = "10") int topN) {
        return Result.success(aggregator.getTopChunks(datasetId, topN));
    }

    /**
     * 文档分类分布（来自 metadata summary）。
     */
    @GetMapping("/category-distribution")
    public Result<Map<String, Long>> categoryDistribution() {
        return Result.success(aggregator.getCategoryDistribution());
    }

    /**
     * 检索性能指标（平均响应耗时、7 天检索量）。
     */
    @GetMapping("/performance")
    public Result<Map<String, Object>> performance() {
        return Result.success(aggregator.getPerformance());
    }
}
