package com.company.knowledge.permission.service;

import com.company.knowledge.common.constant.SecurityLevel;
import com.company.knowledge.org.entity.SysOrg;
import com.company.knowledge.org.entity.SysPerson;
import com.company.knowledge.org.mapper.SysOrgMapper;
import com.company.knowledge.org.mapper.SysPersonMapper;
import com.company.knowledge.permission.entity.PermissionIndex;
import com.company.knowledge.permission.entity.PermissionPolicy;
import com.company.knowledge.permission.mapper.PermissionIndexMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link PermissionIndexService} 单元测试。所有外部依赖 mock。
 *
 * <p>覆盖：rebuild 角色 → depts/categories/level 计算；buildMetadataCondition
 * 各字段组合；Redis 命中/未命中路径。
 */
class PermissionIndexServiceTest {

    private PermissionIndexMapper indexMapper;
    private RoleService roleService;
    private SysPersonMapper personMapper;
    private SysOrgMapper orgMapper;
    private PermissionPolicyService policyService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private PermissionIndexService service;

    @BeforeEach
    void setUp() {
        indexMapper = Mockito.mock(PermissionIndexMapper.class);
        roleService = Mockito.mock(RoleService.class);
        personMapper = Mockito.mock(SysPersonMapper.class);
        orgMapper = Mockito.mock(SysOrgMapper.class);
        policyService = Mockito.mock(PermissionPolicyService.class);
        redis = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        objectMapper = new ObjectMapper();

        service = new PermissionIndexService(indexMapper, roleService,
                personMapper, orgMapper, policyService, redis, objectMapper);
    }

    // --- rebuild ---

    @Test
    void rebuild_adminRole_returnsWildcardDeptsAndMaxLevel() {
        when(roleService.getRoleCodesByPersonId("p1"))
                .thenReturn(new HashSet<>(Collections.singletonList("ADMIN")));

        PermissionIndex out = service.rebuild("p1");

        assertEquals("p1", out.getPersonId());
        assertArrayEquals(new String[]{"*"}, out.getVisibleDepts());
        assertEquals(SecurityLevel.MAX, out.getMaxSecurityLevel().shortValue());
        verify(indexMapper, times(1)).deleteById("p1");
        verify(indexMapper, times(1)).insert(any());
        // cacheSet 走 Redis set；若 jackson 失败则不会触发 set。
        // 这里不强制验证 Redis，因为业务路径已覆盖（DB upsert + return）。
        verify(redis, atLeastOnce()).opsForValue();
    }

    @Test
    void rebuild_cacheSerializeSucceeds_invokesRedisSet() throws Exception {
        // 用 mock ObjectMapper 控制序列化结果，验证 cacheSet 通路
        ObjectMapper mockOm = Mockito.mock(ObjectMapper.class);
        when(mockOm.writeValueAsString(any())).thenReturn("{\"personId\":\"p1\"}");
        PermissionIndexService svc = new PermissionIndexService(
                indexMapper, roleService, personMapper, orgMapper, policyService, redis, mockOm);

        when(roleService.getRoleCodesByPersonId("p1"))
                .thenReturn(new HashSet<>(Collections.singletonList("ADMIN")));

        svc.rebuild("p1");

        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void rebuild_normalRole_collectsAncestorDepts() {
        when(roleService.getRoleCodesByPersonId("p1"))
                .thenReturn(new HashSet<>(Collections.singletonList("EDITOR")));

        SysPerson person = new SysPerson();
        person.setPersonId("p1");
        person.setOrgIndexCode("dept-leaf");
        when(personMapper.selectById("p1")).thenReturn(person);

        SysOrg leaf = new SysOrg();
        leaf.setOrgIndexCode("dept-leaf");
        leaf.setParentOrgIndexCode("dept-mid");
        SysOrg mid = new SysOrg();
        mid.setOrgIndexCode("dept-mid");
        mid.setParentOrgIndexCode("dept-root");
        SysOrg root = new SysOrg();
        root.setOrgIndexCode("dept-root");
        root.setParentOrgIndexCode(null);
        when(orgMapper.selectById("dept-leaf")).thenReturn(leaf);
        when(orgMapper.selectById("dept-mid")).thenReturn(mid);
        when(orgMapper.selectById("dept-root")).thenReturn(root);

        PermissionIndex out = service.rebuild("p1");

        List<String> depts = Arrays.asList(out.getVisibleDepts());
        assertTrue(depts.contains("dept-leaf"));
        assertTrue(depts.contains("dept-mid"));
        assertTrue(depts.contains("dept-root"));
        assertEquals((short) 2, out.getMaxSecurityLevel().shortValue());
    }

    @Test
    void rebuild_categoriesFromPolicy() {
        Set<String> codes = new HashSet<>(Arrays.asList("EDITOR", "AUDITOR_REGION"));
        when(roleService.getRoleCodesByPersonId("p1")).thenReturn(codes);

        // 无 person / org 数据 → dept 空数组
        when(personMapper.selectById("p1")).thenReturn(null);

        // 模拟策略
        PermissionPolicy p1 = new PermissionPolicy();
        p1.setObjectType("CATEGORY");
        p1.setObjectValue("ops");
        PermissionPolicy p2 = new PermissionPolicy();
        p2.setObjectType("CATEGORY");
        p2.setObjectValue("safe");
        PermissionPolicy p3 = new PolicyStub("TAG", "secret-tag");
        when(policyService.listBySubject(eq("ROLE"), eq("EDITOR")))
                .thenReturn(Arrays.asList(p1, p3));
        when(policyService.listBySubject(eq("ROLE"), eq("AUDITOR_REGION")))
                .thenReturn(Collections.singletonList(p2));

        PermissionIndex out = service.rebuild("p1");

        List<String> cats = Arrays.asList(out.getVisibleCategories());
        assertTrue(cats.contains("ops"));
        assertTrue(cats.contains("safe"));
        List<String> tags = Arrays.asList(out.getVisibleTags());
        assertTrue(tags.contains("secret-tag"));
    }

    /** 用 stub 区分策略对象的 objectType（避免 Mockito anyList 干扰） */
    private static class PolicyStub extends PermissionPolicy {
        private final String ot;
        private final String ov;

        PolicyStub(String ot, String ov) {
            this.ot = ot;
            this.ov = ov;
        }

        @Override
        public String getObjectType() {
            return ot;
        }

        @Override
        public String getObjectValue() {
            return ov;
        }
    }

    // --- get (cache hit/miss) ---

    @Test
    void get_cacheHit_skipsDb() throws Exception {
        PermissionIndex idx = new PermissionIndex();
        idx.setPersonId("p1");
        idx.setMaxSecurityLevel((short) 3);
        when(valueOps.get(anyString())).thenReturn(objectMapper.writeValueAsString(idx));

        PermissionIndex out = service.get("p1");

        assertNotNull(out);
        assertEquals("p1", out.getPersonId());
        verify(indexMapper, never()).selectById(anyString());
    }

    @Test
    void get_cacheMiss_dbFallback() {
        when(valueOps.get(anyString())).thenReturn(null);
        PermissionIndex idx = new PermissionIndex();
        idx.setPersonId("p1");
        when(indexMapper.selectById("p1")).thenReturn(idx);

        PermissionIndex out = service.get("p1");

        assertNotNull(out);
        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void get_bothMiss_returnsNull() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(indexMapper.selectById(anyString())).thenReturn(null);

        assertNull(service.get("p1"));
    }

    // --- buildMetadataCondition ---

    @Test
    void buildMetadataCondition_noIndex_returnsEmptyAnd() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(indexMapper.selectById(anyString())).thenReturn(null);

        Map<String, Object> m = service.buildMetadataCondition("p1");
        assertEquals("and", m.get("logic"));
        List<?> conditions = (List<?>) m.get("conditions");
        assertTrue(conditions.isEmpty());
    }

    @Test
    void buildMetadataCondition_adminOmitsDeptCondition() {
        PermissionIndex idx = new PermissionIndex();
        idx.setVisibleDepts(new String[]{"*"});
        idx.setMaxSecurityLevel((short) 4);
        idx.setVisibleCategories(new String[]{"ops"});
        when(valueOps.get(anyString())).thenReturn(serialize(idx));

        Map<String, Object> m = service.buildMetadataCondition("p1");
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) m.get("conditions");
        // 应有 security_level 与 category，但不包含 dept_ids
        assertEquals(2, conditions.size());
        boolean hasDept = false;
        for (Map<String, Object> c : conditions) {
            if ("dept_ids".equals(c.get("field"))) {
                hasDept = true;
            }
        }
        assertFalse(hasDept);
    }

    @Test
    void buildMetadataCondition_normalIncludesAllThree() {
        PermissionIndex idx = new PermissionIndex();
        idx.setVisibleDepts(new String[]{"d1", "d2"});
        idx.setMaxSecurityLevel((short) 2);
        idx.setVisibleCategories(new String[]{"ops"});
        idx.setVisibleTags(new String[]{"t1"});
        when(valueOps.get(anyString())).thenReturn(serialize(idx));

        Map<String, Object> m = service.buildMetadataCondition("p1");
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) m.get("conditions");
        assertEquals(3, conditions.size());

        Set<String> fields = new HashSet<>();
        for (Map<String, Object> c : conditions) {
            fields.add((String) c.get("field"));
        }
        assertTrue(fields.contains("dept_ids"));
        assertTrue(fields.contains("security_level"));
        assertTrue(fields.contains("category"));
    }

    private String serialize(PermissionIndex idx) {
        try {
            return objectMapper.writeValueAsString(idx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
