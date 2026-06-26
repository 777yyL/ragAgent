package com.company.knowledge.org.service;

import com.company.knowledge.integration.hikvision.PersonApi;
import com.company.knowledge.integration.hikvision.dto.HikPerson;
import com.company.knowledge.org.entity.SysPerson;
import com.company.knowledge.org.mapper.SysPersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人员副本同步服务。海康 → 本地表（单向只读 upsert）。
 *
 * <p>策略：
 * <ul>
 *   <li>{@link #fullSync()} 全量分页拉取并 upsert（每周日 02:00）</li>
 *   <li>{@link #incrementalSync(LocalDateTime)} 按时间窗增量；
 *       若 {@code since} 距今 &gt; 40 小时，自动降级为全量（绕过海康 1-48h 红线）</li>
 * </ul>
 *
 * <p>删除处理：海康返回 {@code status < 0} 的记录同步后保留行（软删除），
 * 保证审计可追溯，不物理删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonSyncService {

    /** 海康增量接口允许的最大时间窗（48h），提前 8h 降级为全量 */
    public static final int FULL_SYNC_FALLBACK_HOURS = 40;

    private static final int PAGE_SIZE = 1000;

    private final PersonApi personApi;
    private final SysPersonMapper personMapper;

    /** 全量同步。返回同步条数。 */
    public int fullSync() {
        int pageNo = 1;
        int total = 0;
        while (true) {
            List<HikPerson> page = personApi.listAll(pageNo, PAGE_SIZE);
            if (page == null || page.isEmpty()) break;
            for (HikPerson p : page) {
                upsert(p);
                total++;
            }
            if (page.size() < PAGE_SIZE) break;
            pageNo++;
        }
        log.info("[PersonSync] fullSync done, {} records", total);
        return total;
    }

    /** 增量同步。返回变更条数（含软删除）。 */
    public int incrementalSync(LocalDateTime since) {
        if (since == null) {
            log.warn("[PersonSync] incrementalSync since=null, fallback to full");
            return fullSync();
        }
        LocalDateTime now = LocalDateTime.now();
        if (since.isBefore(now.minusHours(FULL_SYNC_FALLBACK_HOURS))) {
            log.warn("[PersonSync] since={} older than {}h, fallback to full", since, FULL_SYNC_FALLBACK_HOURS);
            return fullSync();
        }
        int pageNo = 1;
        int total = 0;
        while (true) {
            List<HikPerson> page = personApi.listByTimeRange(since, now, pageNo, PAGE_SIZE);
            if (page == null || page.isEmpty()) break;
            for (HikPerson p : page) {
                upsert(p);
                total++;
            }
            if (page.size() < PAGE_SIZE) break;
            pageNo++;
        }
        log.info("[PersonSync] incrementalSync from {} → {} changed", since, total);
        return total;
    }

    /** SSO 登录时按单条 personId 兜底拉取并 upsert。 */
    public SysPerson upsertById(String personId) {
        HikPerson hik = personApi.getById(personId);
        if (hik == null) return null;
        return upsert(hik);
    }

    private SysPerson upsert(HikPerson p) {
        SysPerson entity = personMapper.selectById(p.getPersonId());
        boolean isNew = entity == null;
        if (isNew) entity = new SysPerson();

        entity.setPersonId(p.getPersonId());
        entity.setPersonName(p.getPersonName());
        entity.setOrgIndexCode(p.getOrgIndexCode());
        entity.setOrgPath(p.getOrgPath());
        entity.setOrgPathName(p.getOrgPathName());
        entity.setJobNo(p.getJobNo());
        entity.setCertificateNo(p.getCertificateNo());
        entity.setPhone(p.getPhoneNo());
        entity.setEmail(p.getEmail());
        entity.setCompany(p.getCompany());
        entity.setPost(p.getPost());
        entity.setPostType(p.getPostType());
        entity.setStatus(p.getStatus() != null ? p.getStatus() : 0);
        entity.setSyncTime(LocalDateTime.now());

        if (isNew) personMapper.insert(entity);
        else personMapper.updateById(entity);
        return entity;
    }
}
