package com.company.knowledge.permission.service;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.permission.entity.SysRole;
import com.company.knowledge.permission.mapper.SysPersonRoleMapper;
import com.company.knowledge.permission.mapper.SysRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link RoleService} 单元测试。Mapper 全部 mock，验证业务逻辑而非 SQL。
 */
class RoleServiceTest {

    private SysRoleMapper roleMapper;
    private SysPersonRoleMapper personRoleMapper;
    private RoleService service;

    @BeforeEach
    void setUp() {
        roleMapper = Mockito.mock(SysRoleMapper.class);
        personRoleMapper = Mockito.mock(SysPersonRoleMapper.class);
        service = new RoleService(roleMapper, personRoleMapper);
    }

    @Test
    void getRoleCodesByPersonId_emptyPersonId_returnsEmptySet() {
        assertTrue(service.getRoleCodesByPersonId("").isEmpty());
        assertTrue(service.getRoleCodesByPersonId(null).isEmpty());
    }

    @Test
    void getRoleCodesByPersonId_normal_returnsCodeSet() {
        SysRole r1 = new SysRole();
        r1.setRoleCode("ADMIN");
        SysRole r2 = new SysRole();
        r2.setRoleCode("EDITOR");
        when(roleMapper.selectByPersonId("p1")).thenReturn(Arrays.asList(r1, r2));

        Set<String> codes = service.getRoleCodesByPersonId("p1");
        assertEquals(2, codes.size());
        assertTrue(codes.contains("ADMIN"));
        assertTrue(codes.contains("EDITOR"));
    }

    @Test
    void getRoleCodesByPersonId_noRoles_returnsEmptySet() {
        when(roleMapper.selectByPersonId("p2")).thenReturn(Collections.emptyList());
        assertTrue(service.getRoleCodesByPersonId("p2").isEmpty());
    }

    @Test
    void assignRoles_emptyPersonId_throwsBizException() {
        assertThrows(BizException.class, () -> service.assignRoles("", new HashSet<>()));
        assertThrows(BizException.class, () -> service.assignRoles(null, new HashSet<>()));
    }

    @Test
    void assignRoles_unknownRoleCode_throws4001() {
        SysRole admin = new SysRole();
        admin.setRoleCode("ADMIN");
        admin.setRoleId(1L);
        when(roleMapper.selectList(any())).thenReturn(Collections.singletonList(admin));

        BizException ex = assertThrows(BizException.class,
                () -> service.assignRoles("p1", new HashSet<>(Arrays.asList("ADMIN", "GHOST"))));
        assertEquals(4001, ex.getCode());
    }

    @Test
    void assignRoles_emptyCodes_clearsOnly() {
        service.assignRoles("p1", Collections.emptySet());
        verify(personRoleMapper, times(1)).deleteByPersonId("p1");
        verify(personRoleMapper, never()).insert(any());
    }

    @Test
    void assignRoles_normal_firstDeleteThenInsert() {
        SysRole admin = new SysRole();
        admin.setRoleId(1L);
        admin.setRoleCode("ADMIN");
        SysRole editor = new SysRole();
        editor.setRoleId(5L);
        editor.setRoleCode("EDITOR");
        when(roleMapper.selectList(any())).thenReturn(Arrays.asList(admin, editor));

        service.assignRoles("p1", new HashSet<>(Arrays.asList("ADMIN", "EDITOR")));

        // 1. 先删
        verify(personRoleMapper, times(1)).deleteByPersonId("p1");
        // 2. 后插，2 行
        verify(personRoleMapper, times(2)).insert(any());
        // 3. 没有额外 delete 调用
        verifyNoMoreInteractions(personRoleMapper);
    }

    @Test
    void listByPersonId_emptyPersonId_returnsEmptyList() {
        assertTrue(service.listByPersonId("").isEmpty());
        assertTrue(service.listByPersonId(null).isEmpty());
    }
}
