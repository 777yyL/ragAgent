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
 * 增量同步任务。建议调度：每小时。
 *
 * <p>从上次成功同步时间拉取变更（含软删除）。
 * 若上次同步距今 > 40 小时（超过海康 1-48h 红线），
 * {@code PersonSyncService}/{@code OrgSyncService} 内部自动降级为全量。
 *
 * <p>同步成功后更新 last_sync_time；失败不更新（下次仍从旧游标拉取）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncrementalSyncJob {

    private final OrgSyncService orgSync;
    private final PersonSyncService personSync;
    private final SyncStateService state;

    @XxlJob("incrementalSyncJob")
    public void execute() {
        log.info("[IncrementalSync] start");
        long t0 = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        try {
            LocalDateTime orgSince = state.getLastOrgSync();
            if (orgSince == null) orgSince = now.minusHours(1);
            int orgChanged = orgSync.incrementalSync(orgSince);
            state.setOrgSync(now);

            LocalDateTime personSince = state.getLastPersonSync();
            if (personSince == null) personSince = now.minusHours(1);
            int personChanged = personSync.incrementalSync(personSince);
            state.setPersonSync(now);

            log.info("[IncrementalSync] done in {}ms, orgs={}, persons={}",
                    System.currentTimeMillis() - t0, orgChanged, personChanged);
        } catch (Exception e) {
            log.warn("[IncrementalSync] failed after {}ms (hikvision unreachable?), skip: {}",
                    System.currentTimeMillis() - t0, e.getMessage());
            // dev 模式下海康不可达不重抛，Job 不报失败
        }
    }
}
