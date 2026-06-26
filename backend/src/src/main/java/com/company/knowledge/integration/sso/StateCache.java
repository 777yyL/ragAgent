package com.company.knowledge.integration.sso;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * OAuth2 CSRF state 缓存。
 *
 * <p>{@code /sso/login} 颁发 state（5 分钟 TTL）写入 Redis，
 * {@code /sso/callback} 拿到 state 后调 {@link #consume(String)} 一次性校验+删除，
 * 防 CSRF 攻击与重放。
 */
@Component
@RequiredArgsConstructor
public class StateCache {

    private static final String PREFIX = "knowledge:oauth2:state:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    /** 颁发新 state（一次性使用） */
    public String issue() {
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(PREFIX + state, "1", TTL);
        return state;
    }

    /**
     * 校验并消费（一次性）。
     *
     * @return true 表示 state 有效且已删除；false 表示不存在或已过期
     */
    public boolean consume(String state) {
        if (state == null || state.isEmpty()) return false;
        Boolean deleted = redis.delete(PREFIX + state);
        return Boolean.TRUE.equals(deleted);
    }
}
