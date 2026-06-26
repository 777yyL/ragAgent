package com.company.knowledge.audit.constant;

/**
 * 审核节点动作枚举，记录于 {@code audit_node_record.action}。
 */
public enum AuditAction {
    /** 提交 */
    SUBMIT,
    /** 通过 */
    APPROVE,
    /** 退回 */
    REJECT,
    /** 撤回 */
    WITHDRAW
}
