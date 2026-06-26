package com.company.knowledge.integration.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 本地会话管理（Redis 存储）。
 *
 * <p>SSO 登录成功后由 {@code OAuth2Controller} 调 {@link #create(SessionUser)} 签发 sessionId，
 * 写入 Redis（2 小时 TTL），通过 Cookie 下发给浏览器。
 *
 * <p>{@link SessionInterceptor} 在每个请求开始时调 {@link #get(String)} 注入 {@code UserContext}。
 */
@Slf4j
@Component
public class SessionManager {

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "knowledge:session:";

    /** 会话有效期：2 小时 */
    public static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public SessionManager(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public String create(SessionUser user) {
        String sessionId = newSessionId();
        try {
            redis.opsForValue().set(KEY_PREFIX + sessionId, mapper.writeValueAsString(user), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("session create failed", e);
        }
        return sessionId;
    }

    public SessionUser get(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        String json = redis.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null) return null;
        try {
            return mapper.readValue(json, SessionUser.class);
        } catch (Exception e) {
            log.warn("session parse failed, will invalidate: {}", e.getMessage());
            redis.delete(KEY_PREFIX + sessionId);
            return null;
        }
    }

    public void invalidate(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            redis.delete(KEY_PREFIX + sessionId);
        }
    }

    /** 续期（滑动会话）——可由拦截器在活跃请求时调用 */
    public void renew(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            redis.expire(KEY_PREFIX + sessionId, TTL);
        }
    }

    private static String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 会话内的用户信息。序列化为 JSON 存 Redis。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionUser {
        private String personId;
        private String personName;
        private String orgIndexCode;
        private String orgPath;
        /** 本地角色 code 集合（如 ADMIN/AUDITOR_GROUP/EDITOR） */
        private java.util.Set<String> roles;
    }
}
