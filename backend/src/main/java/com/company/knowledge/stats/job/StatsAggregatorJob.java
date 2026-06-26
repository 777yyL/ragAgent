package com.company.knowledge.stats.job;

import com.company.knowledge.stats.service.StatsAggregator;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统计聚合定时任务。
 *
 * <p>在 {@code xxl-job-admin} 配置 JobHandler = {@code statsAggregatorJob}，
 * 建议每天凌晨 02:00 执行一次，把当日统计快照写入 Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsAggregatorJob {

    private final StatsAggregator aggregator;

    @XxlJob("statsAggregatorJob")
    public void statsAggregatorJob() {
        log.info("[statsAggregatorJob] start");
        long t = System.currentTimeMillis();
        try {
            aggregator.aggregateDaily();
            log.info("[statsAggregatorJob] done, cost={}ms", System.currentTimeMillis() - t);
        } catch (Exception e) {
            log.error("[statsAggregatorJob] failed", e);
            throw e;
        }
    }
}
