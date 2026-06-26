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
public class IncrementalSyncJob {
    private final OrgSyncService orgSync;
    private final PersonSyncService personSync;
    private final SyncStateService state;

    @Scheduled(cron = "0 0 * * * ?")
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

            log.info("[IncrementalSync] done in {}ms, orgs={}, persons={}", System.currentTimeMillis() - t0, orgChanged, personChanged);
        } catch (Exception e) {
            log.warn("[IncrementalSync] failed: {}", e.getMessage());
        }
    }
}
