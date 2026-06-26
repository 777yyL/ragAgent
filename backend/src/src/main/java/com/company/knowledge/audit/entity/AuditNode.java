package com.company.knowledge.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核节点定义，存于 {@code audit_template.nodes} JSONB 数组中。
 *
 * <p>{@link #order} 从 1 开始，1 为初审，最大值为终审（终审通过触发发布）。
 *
 * <p>{@link #approverRole} 取自 {@code sys_role.role_code}，如
 * {@code AUDITOR_ENTERPRISE} / {@code AUDITOR_REGION} / {@code AUDITOR_GROUP}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditNode {

    /** 节点顺序，1=初审，递增到终审 */
    private int order;

    /** 节点名称，如「企业初审」「集团终审」 */
    private String name;

    /** 审批角色 code，对应 sys_role.role_code */
    private String approverRole;

    /** 是否会签（true=所有该角色用户都需通过，false=任一即可）。当前实现按 false 处理 */
    private boolean multiSign;
}
