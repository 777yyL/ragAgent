package com.company.knowledge.integration.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SessionManager 单元测试。用 Mockito 模拟 Redis，验证：
 * <ul>
 *   <li>create 写入 JSON + 2h TTL</li>
 *   <li>get 反序列化已存 JSON</li>
 *   <li>get(null) / get(未知) 返回 null</li>
 *   <li>invalidate 调 delete</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    private SessionManager manager;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        manager = new SessionManager(redis, mapper);
    }

    @Test
    void create_shouldStoreJsonWith2hTtl() {
        SessionManager.SessionUser user = sampleUser();

        String sid = manager.create(user);

        assertNotNull(sid);
        assertEquals(32, sid.length());
        verify(valueOps).set(eq("knowledge:session:" + sid), contains("\"personId\":\"p1\""), eq(Duration.ofHours(2)));
    }

    @Test
    void get_shouldDeserializeStoredJson() throws Exception {
        SessionManager.SessionUser user = sampleUser();
        String json = mapper.writeValueAsString(user);
        when(valueOps.get("knowledge:session:sid1")).thenReturn(json);

        SessionManager.SessionUser loaded = manager.get("sid1");

        assertNotNull(loaded);
        assertEquals("p1", loaded.getPersonId());
        assertEquals("张三", loaded.getPersonName());
        assertEquals("org01", loaded.getOrgIndexCode());
        assertTrue(loaded.getRoles().contains("EDITOR"));
    }

    @Test
    void get_nullSid_shouldReturnNull() {
        assertNull(manager.get(null));
        assertNull(manager.get(""));
    }

    @Test
    void get_unknownSid_redisReturnNull_shouldReturnNull() {
        when(valueOps.get("knowledge:session:unknown")).thenReturn(null);
        assertNull(manager.get("unknown"));
    }

    @Test
    void get_corruptJson_shouldInvalidateAndReturnNull() {
        when(valueOps.get("knowledge:session:bad")).thenReturn("not-a-json");
        assertNull(manager.get("bad"));
        verify(redis).delete("knowledge:session:bad");
    }

    @Test
    void invalidate_shouldCallDelete() {
        manager.invalidate("sid2");
        verify(redis).delete("knowledge:session:sid2");
    }

    @Test
    void renew_shouldResetTtl() {
        manager.renew("sid3");
        verify(redis).expire("knowledge:session:sid3", Duration.ofHours(2));
    }

    private SessionManager.SessionUser sampleUser() {
        return new SessionManager.SessionUser(
                "p1", "张三", "org01", "/root/集团", Set.of("EDITOR"));
    }
}
