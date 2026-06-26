package com.company.knowledge.org.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 同步状态服务。用 Redis 记录上次成功同步时间，供增量同步游标使用。
 *
 * <p>两个独立的 key（人员 / 组织），支持不同的同步频率。
 */
@Service
@RequiredArgsConstructor
public class SyncStateService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String PERSON_KEY = "knowledge:sync:person:last_time";
    private static final String ORG_KEY = "knowledge:sync:org:last_time";

    private final StringRedisTemplate redis;

    public LocalDateTime getLastPersonSync() {
        return get(PERSON_KEY);
    }

    public void setPersonSync(LocalDateTime time) {
        set(PERSON_KEY, time);
    }

    public LocalDateTime getLastOrgSync() {
        return get(ORG_KEY);
    }

    public void setOrgSync(LocalDateTime time) {
        set(ORG_KEY, time);
    }

    private LocalDateTime get(String key) {
        String v = redis.opsForValue().get(key);
        if (v == null || v.isEmpty()) return null;
        try {
            return LocalDateTime.parse(v, FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void set(String key, LocalDateTime time) {
        redis.opsForValue().set(key, time.format(FMT));
    }
}
