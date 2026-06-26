package com.company.knowledge.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色。对应表 {@code sys_role}。
 *
 * <p>内置 6 个角色（{@code is_builtin=true}）：ADMIN / AUDITOR_GROUP /
 * AUDITOR_REGION / AUDITOR_ENTERPRISE / EDITOR / VIEWER，由 V1 schema 预置。
 * 业务可扩展自定义角色。
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 主键，BIGSERIAL */
    @TableId(type = IdType.AUTO)
    private Long roleId;

    /** 角色编码，全局唯一，作为权限策略 subject_id 使用 */
    private String roleCode;

    /** 角色显示名 */
    private String roleName;

    /** 描述 */
    private String description;

    /** 是否内置角色（内置角色禁止删除） */
    private Boolean isBuiltin;

    private LocalDateTime createdAt;
}
