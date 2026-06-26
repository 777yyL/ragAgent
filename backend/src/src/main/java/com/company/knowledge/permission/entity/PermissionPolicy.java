package com.company.knowledge.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.knowledge.permission.handler.StringArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限策略（四维模型）。对应表 {@code permission_policy}。
 *
 * <p>四维：subject（ROLE/DEPT/USER）+ object（DATASET/CATEGORY/TAG/DOC）
 * + actions（VIEW/SEARCH/EDIT/DELETE/AUDIT/PUBLISH/EXPORT）+ inherit。
 *
 * <p>{@link #actions} 使用 PG {@code VARCHAR[]} 类型，通过
 * {@link StringArrayTypeHandler} 与 {@code String[]} 互转。
 */
@Data
@TableName(value = "permission_policy", autoResultMap = true)
public class PermissionPolicy {

    /** 主键，BIGSERIAL */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主体类型：ROLE / DEPT / USER */
    private String subjectType;

    /** 主体 ID（roleCode / orgIndexCode / personId） */
    private String subjectId;

    /** 客体类型：DATASET / CATEGORY / TAG / DOC */
    private String objectType;

    /** 客体值（dataset_id / category_code / tag / doc_id） */
    private String objectValue;

    /**
     * 动作集合，PG {@code VARCHAR[]}。
     *
     * <p>使用 {@link StringArrayTypeHandler} 处理；空数组在 PG 中合法。
     */
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] actions;

    /** 是否继承到下级对象（默认 true，如部门继承到子部门文档） */
    private Boolean inherit;

    /** 创建人 personId */
    private String createdBy;

    private LocalDateTime createdAt;
}
