package com.company.knowledge.stats.job;

import com.company.knowledge.stats.service.StatsAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
