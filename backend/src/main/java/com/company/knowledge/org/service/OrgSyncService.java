package com.company.knowledge.org.service;

import com.company.knowledge.integration.hikvision.OrgApi;
import com.company.knowledge.integration.hikvision.dto.HikOrg;
import com.company.knowledge.org.entity.SysOrg;
import com.company.knowledge.org.mapper.SysOrgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织副本同步服务。海康 → 本地表（单向只读 upsert）。
 *
 * <p>全量同步从根组织开始递归下钻；增量用时间窗接口（同样含 status&lt;0 软删除）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgSyncService {

    public static final int FULL_SYNC_FALLBACK_HOURS = 40;
    private static final int PAGE_SIZE = 1000;

    private final OrgApi orgApi;
    private final SysOrgMapper orgMapper;

    public int fullSync() {
        int total = 0;
        List<HikOrg> roots = orgApi.listRoot();
        if (roots == null || roots.isEmpty()) {
            log.warn("[OrgSync] no root org found");
            return 0;
        }
        for (HikOrg root : roots) {
            total += upsertAndRecurse(root, 0);
        }
        log.info("[OrgSync] fullSync done, {} orgs", total);
        return total;
    }

    /**
     * 增量同步（含软删除）。若 since 超过 {@link #FULL_SYNC_FALLBACK_HOURS} 小时，
     * 自动降级为全量（避免海康 1-48h 红线）。
     */
    public int incrementalSync(LocalDateTime since) {
        if (since == null) {
            log.warn("[OrgSync] incrementalSync since=null, fallback to full");
            return fullSync();
        }
        LocalDateTime now = LocalDateTime.now();
        if (since.isBefore(now.minusHours(FULL_SYNC_FALLBACK_HOURS))) {
            log.warn("[OrgSync] since={} older than {}h, fallback to full", since, FULL_SYNC_FALLBACK_HOURS);
            return fullSync();
        }
        int pageNo = 1;
        int total = 0;
        while (true) {
            List<HikOrg> page = orgApi.listByTimeRange(since, now, pageNo, PAGE_SIZE);
            if (page == null || page.isEmpty()) break;
            for (HikOrg o : page) {
                upsert(o);
                total++;
            }
            if (page.size() < PAGE_SIZE) break;
            pageNo++;
        }
        log.info("[OrgSync] incrementalSync from {} → {} changed", since, total);
        return total;
    }

    /** 递归 upsert（仅全量路径使用）。防环深度保护。 */
    private int upsertAndRecurse(HikOrg org, int depth) {
        if (depth > 32) {
            log.warn("[OrgSync] depth {} exceeded on {}, stop recursion", depth, org.getOrgIndexCode());
            return 0;
        }
        upsert(org);
        int count = 1;
        if (org.getLeaf() == null || !org.getLeaf()) {
            List<HikOrg> subs = orgApi.listSub(org.getOrgIndexCode());
            if (subs != null) {
                for (HikOrg sub : subs) {
                    count += upsertAndRecurse(sub, depth + 1);
                }
            }
        }
        return count;
    }

    private void upsert(HikOrg o) {
        SysOrg entity = orgMapper.selectById(o.getOrgIndexCode());
        boolean isNew = entity == null;
        if (isNew) entity = new SysOrg();

        entity.setOrgIndexCode(o.getOrgIndexCode());
        entity.setOrgName(o.getOrgName());
        entity.setOrgPath(o.getOrgPath());
        entity.setParentOrgIndexCode(o.getParentOrgIndexCode());
        entity.setIsLeaf(o.getLeaf());
        entity.setSort(o.getSort());
        entity.setAvailable(o.getAvailable());
        entity.setStatus(o.getStatus() != null ? o.getStatus() : 0);
        entity.setSyncTime(LocalDateTime.now());

        if (isNew) orgMapper.insert(entity);
        else orgMapper.updateById(entity);
    }
}
