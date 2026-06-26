package com.company.knowledge.stats.job;

import com.company.knowledge.stats.service.StatsAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计聚合定时任务。每天凌晨 02:00 执行，把当日统计快照写入 Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsAggregatorJob {

    private final StatsAggregator aggregator;

    @Scheduled(cron = "0 0 2 * * ?")
    public void execute() {
        log.info("[StatsAggregator] start");
        long t = System.currentTimeMillis();
        try {
            aggregator.aggregateDaily();
            log.info("[StatsAggregator] done, cost={}ms", System.currentTimeMillis() - t);
        } catch (Exception e) {
            log.error("[StatsAggregator] failed", e);
        }
    }
}
