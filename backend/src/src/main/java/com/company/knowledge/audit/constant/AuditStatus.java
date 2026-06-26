package com.company.knowledge.audit.constant;

/**
 * 审核实例状态枚举。
 *
 * <p>合法转换路径：
 * <ul>
 *   <li>DRAFT → PENDING（submit）</li>
 *   <li>PENDING → PENDING（approve 进入下一节点）</li>
 *   <li>PENDING → APPROVED（approve 到终审节点）</li>
 *   <li>PENDING → REJECTED（reject，可重新提交）</li>
 *   <li>PENDING → WITHDRAWN（withdraw，由提交人撤回）</li>
 *   <li>REJECTED → DRAFT（编辑员修改后重新 submit 复用同一实例或新建）</li>
 *   <li>APPROVED → PUBLISHED（终审后由 PublishService 触发）</li>
 * </ul>
 */
public enum AuditStatus {
    /** 草稿（未提交或退回后回到草稿态） */
    DRAFT,
    /** 审核中（提交后正在被审批） */
    PENDING,
    /** 审核通过（终审节点 approve，等待发布） */
    APPROVED,
    /** 审核退回（某节点 reject） */
    REJECTED,
    /** 撤回（提交人主动撤回） */
    WITHDRAWN,
    /** 已发布（终审通过且 chunks 已 available=true） */
    PUBLISHED
}
