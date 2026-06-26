package com.company.knowledge.common.constant;

/**
 * 文档密级常量。
 *
 * <p>用于 knowledge_doc.security_level 与 permission_index.max_security_level：
 *
 * <ul>
 *   <li>{@link #PUBLIC} = 1（公开，所有人可见）</li>
 *   <li>{@link #INTERNAL} = 2（内部，企业员工可见）</li>
 *   <li>{@link #CONFIDENTIAL} = 3（机密，区域/集团审核员可见）</li>
 *   <li>{@link #SECRET} = 4（绝密，仅 ADMIN 可见）</li>
 * </ul>
 *
 * <p>{@code max_security_level} 表示该用户能看到的最大密级；
 * 检索时 RAGFlow metadata_condition 用 {@code security_level <= max_security_level} 过滤。
 */
public final class SecurityLevel {

    private SecurityLevel() {
    }

    /** 公开：所有人可见 */
    public static final short PUBLIC = 1;

    /** 内部：企业员工可见 */
    public static final short INTERNAL = 2;

    /** 机密：区域/集团审核员可见 */
    public static final short CONFIDENTIAL = 3;

    /** 绝密：仅 ADMIN 可见 */
    public static final short SECRET = 4;

    /** 最大值（= {@link #SECRET}），用于 ADMIN */
    public static final short MAX = SECRET;
}
