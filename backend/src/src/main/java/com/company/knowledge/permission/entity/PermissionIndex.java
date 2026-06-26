package com.company.knowledge.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.knowledge.permission.handler.StringArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限索引（按 personId 预计算）。对应表 {@code permission_index}。
 *
 * <p>检索时直接读这张表，避免在 SearchService 每次拼权限 SQL。
 * 由 {@code PermissionIndexService.rebuild} 在登录或角色变更后重算。
 *
 * <p>三个 {@code visible_*} 字段均为 PG {@code VARCHAR[]}，
 * 通过 {@link StringArrayTypeHandler} 与 {@code String[]} 互转。
 */
@Data
@TableName(value = "permission_index", autoResultMap = true)
public class PermissionIndex {

    /** 海康 personId（主键） */
    @TableId(type = IdType.INPUT)
    private String personId;

    /** 可见部门 orgIndexCode 列表 */
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] visibleDepts;

    /** 可见分类（业务类别）列表 */
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] visibleCategories;

    /** 可见标签列表 */
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] visibleTags;

    /** 可见最大密级（1-4），低于等于此值的文档对该用户可见 */
    private Short maxSecurityLevel;

    private LocalDateTime updatedAt;
}
