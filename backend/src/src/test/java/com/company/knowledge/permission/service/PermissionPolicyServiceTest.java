package com.company.knowledge.permission.service;

import com.company.knowledge.permission.entity.PermissionPolicy;
import com.company.knowledge.permission.mapper.PermissionPolicyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link PermissionPolicyService} 单元测试。Mapper mock，验证业务逻辑与 wrapper 条件。
 */
class PermissionPolicyServiceTest {

    private PermissionPolicyMapper mapper;
    private PermissionPolicyService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(PermissionPolicyMapper.class);
        service = new PermissionPolicyService(mapper);
    }

    @Test
    void create_insertAndReturnPolicy() {
        PermissionPolicy p = new PermissionPolicy();
        p.setSubjectType("ROLE");
        p.setSubjectId("EDITOR");
        p.setObjectType("CATEGORY");
        p.setObjectValue("ops");
        p.setActions(new String[]{"VIEW", "SEARCH"});
        when(mapper.insert(any())).thenReturn(1);

        PermissionPolicy out = service.create(p);

        assertSame(p, out);
        verify(mapper, times(1)).insert(p);
    }

    @Test
    void delete_callsDeleteById() {
        service.delete(123L);
        verify(mapper, times(1)).deleteById(123L);
    }

    @Test
    void listBySubject_normal_returnsList() {
        PermissionPolicy p1 = new PermissionPolicy();
        p1.setId(1L);
        p1.setSubjectType("ROLE");
        p1.setSubjectId("ADMIN");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(p1));

        List<PermissionPolicy> out = service.listBySubject("ROLE", "ADMIN");
        assertEquals(1, out.size());
        assertSame(p1, out.get(0));
    }

    @Test
    void listByObject_normal_returnsList() {
        when(mapper.selectList(any())).thenReturn(Arrays.asList());
        List<PermissionPolicy> out = service.listByObject("CATEGORY", "ops");
        assertTrue(out.isEmpty());
    }

    @Test
    void listAll_normal_returnsList() {
        PermissionPolicy p1 = new PermissionPolicy();
        p1.setId(1L);
        PermissionPolicy p2 = new PermissionPolicy();
        p2.setId(2L);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(p1, p2));

        List<PermissionPolicy> out = service.listAll();
        assertEquals(2, out.size());
    }
}
