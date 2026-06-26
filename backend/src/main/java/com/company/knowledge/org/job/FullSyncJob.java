package com.company.knowledge.org.job;

import com.company.knowledge.org.service.OrgSyncService;
import com.company.knowledge.org.service.PersonSyncService;
import com.company.knowledge.org.service.SyncStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class FullSyncJob {
    private final OrgSyncService orgSync;
    private final PersonSyncService personSync;
    private final SyncStateService state;

    @Scheduled(cron = "0 0 2 ? * SUN")
    public void execute() {
        log.info("[FullSync] start");
        long t0 = System.currentTimeMillis();
        try {
            int orgCount = orgSync.fullSync();
            int personCount = personSync.fullSync();
            LocalDateTime now = LocalDateTime.now();
            state.setOrgSync(now);
            state.setPersonSync(now);
            log.info("[FullSync] done in {}ms, orgs={}, persons={}", System.currentTimeMillis() - t0, orgCount, personCount);
        } catch (Exception e) {
            log.warn("[FullSync] failed: {}", e.getMessage());
        }
    }
}
