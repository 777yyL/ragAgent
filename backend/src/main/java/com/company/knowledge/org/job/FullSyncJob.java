package com.company.knowledge.org.job;

import com.company.knowledge.org.service.OrgSyncService;
import com.company.knowledge.org.service.PersonSyncService;
import com.company.knowledge.org.service.SyncStateService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 全量同步任务。建议调度：每周日 02:00。
 *
 * <p>全量拉取人员与组织，重置同步时间游标。用于初始化、纠偏、修复增量遗漏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FullSyncJob {

    private final OrgSyncService orgSync;
    private final PersonSyncService personSync;
    private final SyncStateService state;

    @XxlJob("fullSyncJob")
    public void execute() {
        log.info("[FullSync] start");
        long t0 = System.currentTimeMillis();
        try {
            int orgCount = orgSync.fullSync();
            int personCount = personSync.fullSync();
            LocalDateTime now = LocalDateTime.now();
            state.setOrgSync(now);
            state.setPersonSync(now);
            log.info("[FullSync] done in {}ms, orgs={}, persons={}",
                    System.currentTimeMillis() - t0, orgCount, personCount);
        } catch (Exception e) {
            log.warn("[FullSync] failed after {}ms (hikvision unreachable?), skip: {}",
                    System.currentTimeMillis() - t0, e.getMessage());
            // dev 模式下海康不可达不重抛，Job 不报失败
        }
    }
}
