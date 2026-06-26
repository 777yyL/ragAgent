package com.company.knowledge.stats.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统计快照 DTO。每日聚合任务写入 Redis，仪表盘读取 + 实时补全。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code totalDocs}：所有 dataset 文档总数（来自 DatasetApi.list 聚合）</li>
 *   <li>{@code totalChunks}：所有 dataset chunk 总数（来自 MetadataApi.summary 累加）</li>
 *   <li>{@code searchCountToday}：当日检索次数（SearchLogMapper.countByDateRange）</li>
 *   <li>{@code avgResponseMs}：当日平均响应耗时</li>
 *   <li>{@code topKeywords}：热门检索词 TopN（关键词 + 计数）</li>
 *   <li>{@code categoryDistribution}：按业务分类的文档分布</li>
 *   <li>{@code snapshotAt}：快照生成时间</li>
 * </ul>
 */
@Data
public class StatsSnapshot {

    private long totalDocs;
    private long totalChunks;
    private long searchCountToday;
    private double avgResponseMs;

    /** 元素：{@code {keyword, cnt}} */
    private List<Map<String, Object>> topKeywords;

    /** key=分类，value=数量 */
    private Map<String, Long> categoryDistribution;

    /** 快照生成时间，毫秒时间戳 */
    private long snapshotAt;
}
