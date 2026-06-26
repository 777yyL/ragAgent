package com.company.knowledge.permission.controller;

import com.company.knowledge.common.result.Result;
import com.company.knowledge.permission.entity.SysRole;
import com.company.knowledge.permission.service.RoleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色 REST 端点。
 *
 * <ul>
 *   <li>{@code GET /api/roles} - 列出全部角色</li>
 *   <li>{@code GET /api/roles/persons/{personId}} - 查某人的角色 code 集合</li>
 *   <li>{@code POST /api/roles/persons/{personId}/assign} - 给某人分配角色（先删后插）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 列出全部角色。
     *
     * @return 角色记录列表
     */
    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.success(roleService.listAll());
    }

    /**
     * 查询某人当前的角色 code 集合。
     *
     * @param personId 海康 personId
     * @return 角色 code 字符串集合
     */
    @GetMapping("/persons/{personId}")
    public Result<Set<String>> listByPerson(@PathVariable("personId") String personId) {
        return Result.success(roleService.getRoleCodesByPersonId(personId));
    }

    /**
     * 给某人分配角色（先删后插）。
     *
     * @param personId 海康 personId
     * @param body     请求体，{@code roleCodes} 字段为角色 code 数组
     * @return 成功响应
     */
    @PostMapping("/persons/{personId}/assign")
    public Result<Void> assign(@PathVariable("personId") String personId,
                               @RequestBody AssignRequest body) {
        Set<String> codes = body == null ? new HashSet<>() : body.getRoleCodes();
        roleService.assignRoles(personId, codes);
        return Result.success();
    }

    /** 分配请求体 */
    @Data
    public static class AssignRequest {
        /** 角色 code 集合 */
        private Set<String> roleCodes;
    }
}
