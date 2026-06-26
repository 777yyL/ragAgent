package com.company.knowledge.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.knowledge.audit.handler.AuditNodeTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核模板。对应表 {@code audit_template}。
 *
 * <p>{@link #nodes} 是 JSONB 列，存储 {@code List<AuditNode>}，通过
 * {@link AuditNodeTypeHandler} 用 Jackson 序列化/反序列化。
 *
 * <p>模板示例：
 * <ul>
 *   <li>一级直审：单节点 {@code AUDITOR_GROUP}</li>
 *   <li>两级：企业初审 {@code AUDITOR_ENTERPRISE} → 集团终审 {@code AUDITOR_GROUP}</li>
 *   <li>三级：企业 → 区域 → 集团</li>
 * </ul>
 */
@Data
@TableName(value = "audit_template", autoResultMap = true)
public class AuditTemplate {

    /** 主键，BIGSERIAL */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名称 */
    private String name;

    /** 业务分类，对应 {@code knowledge_doc.business_type}，可为 null 表示通用 */
    private String businessType;

    /**
     * 审核节点列表，JSONB 列。
     *
     * <p>使用 {@link AuditNodeTypeHandler} 与 PG JSONB 互转。
     */
    @TableField(value = "nodes", typeHandler = AuditNodeTypeHandler.class)
    private List<AuditNode> nodes;

    /** 是否启用 */
    private Boolean enabled;

    private LocalDateTime createdAt;
}
