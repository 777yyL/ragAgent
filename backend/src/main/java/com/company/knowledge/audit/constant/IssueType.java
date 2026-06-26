package com.company.knowledge.audit.constant;

/**
 * AI 审核问题类型枚举。6 大类检测对应 {@code ai_audit_issue.issue_type}。
 *
 * <p>对应 {@link com.company.knowledge.audit.service.AiAuditService} 的 6 类检测。
 */
public enum IssueType {
    /** 矛盾检测：相似 chunk 间内容矛盾 */
    CONFLICT,
    /** 错误检测：数值范围/单位等硬错误（规则 + LLM） */
    ERROR,
    /** 时效性：引用标准/规程版本过期 */
    TIMELINESS,
    /** 完整性：关键字段/章节缺失 */
    INTEGRITY,
    /** 一致性：同一实体在全库表述不一致 */
    CONSISTENCY,
    /** 规范性：术语词典不匹配 */
    NORM
}
