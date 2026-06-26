package com.company.knowledge.permission.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.permission.entity.SysPersonRole;
import com.company.knowledge.permission.entity.SysRole;
import com.company.knowledge.permission.mapper.SysPersonRoleMapper;
import com.company.knowledge.permission.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色 Service。负责：
 *
 * <ul>
 *   <li>角色记录 CRUD（基于 sys_role）</li>
 *   <li>人员-角色映射（基于 sys_person_role，先删后插策略）</li>
 *   <li>给 OAuth2Controller 提供按 personId 查询角色 code 集合的能力</li>
 * </ul>
 *
 * <p>不维护缓存（权限索引由 {@code PermissionIndexService} 缓存）；
 * 不校验 personId 是否存在于 sys_person（允许先建关系后同步人员）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysPersonRoleMapper personRoleMapper;

    /**
     * 列出全部角色记录。
     *
     * @return 角色记录列表，按 role_id 升序
     */
    public List<SysRole> listAll() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByAsc(SysRole::getRoleId));
    }

    /**
     * 列出某人的角色完整记录。
     *
     * @param personId 海康 personId，非空
     * @return 角色记录列表，可能为空
     */
    public List<SysRole> listByPersonId(String personId) {
        if (personId == null || personId.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectByPersonId(personId);
    }

    /**
     * 给某人分配角色（先删后插，事务保证一致性）。
     *
     * <p>roleCodes 中不存在的 role_code 直接抛 {@link BizException}（4001），
     * 避免静默漏配。已分配但未在本次列表中的角色会被清除。
     *
     * @param personId  海康 personId
     * @param roleCodes 角色 code 集合，可为空集（等于清空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String personId, Set<String> roleCodes) {
        if (personId == null || personId.isEmpty()) {
            throw BizException.of(4001, "personId is required");
        }
        Set<String> codes = roleCodes == null ? Collections.emptySet() : roleCodes;

        // 1. 校验 roleCodes 全部存在
        if (!codes.isEmpty()) {
            List<SysRole> matched = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                    .in(SysRole::getRoleCode, codes));
            if (matched.size() != codes.size()) {
                Set<String> found = matched.stream()
                        .map(SysRole::getRoleCode)
                        .collect(Collectors.toSet());
                Set<String> missing = new HashSet<>(codes);
                missing.removeAll(found);
                throw BizException.of(4001, "unknown role codes: " + missing);
            }
            // 2. 先删
            personRoleMapper.deleteByPersonId(personId);
            // 3. 后插
            for (SysRole role : matched) {
                SysPersonRole rel = new SysPersonRole();
                rel.setPersonId(personId);
                rel.setRoleId(role.getRoleId());
                personRoleMapper.insert(rel);
            }
        } else {
            // 空集：仅清空
            personRoleMapper.deleteByPersonId(personId);
        }
        log.info("assignRoles ok, personId={}, roleCodes={}", personId, codes);
    }

    /**
     * 按 personId 查询角色 code 集合（供 OAuth2Controller 登录后写入 UserContext.roles）。
     *
     * @param personId 海康 personId
     * @return 角色 code 集合，永不返回 null
     */
    public Set<String> getRoleCodesByPersonId(String personId) {
        if (personId == null || personId.isEmpty()) {
            return Collections.emptySet();
        }
        List<SysRole> roles = roleMapper.selectByPersonId(personId);
        if (roles.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> codes = new HashSet<>(roles.size() * 2);
        for (SysRole r : roles) {
            codes.add(r.getRoleCode());
        }
        return codes;
    }
}
