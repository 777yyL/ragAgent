package com.company.knowledge.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核实例。对应表 {@code audit_instance}。
 *
 * <p>一个文档可多次提交，每次提交生成新实例（旧的 reject/withdraw 实例保留作为历史）。
 * {@link #currentNode} 从 1 开始，对应 {@link AuditTemplate#getNodes()} 的 order。
 */
@Data
@TableName("audit_instance")
public class AuditInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联文档 ID（{@code knowledge_doc.id}） */
    private Long docId;

    /** 使用的模板 ID */
    private Long templateId;

    /** 当前节点序号，从 1 开始 */
    private Integer currentNode;

    /**
     * 状态。见 {@link com.company.knowledge.audit.constant.AuditStatus}。
     * 存储为 VARCHAR(20)，MyBatis-Plus 默认按字符串 name() 存取。
     */
    private String status;

    /** 提交人 personId */
    private String submittedBy;

    private LocalDateTime submittedAt;

    /** 审核结束（approve 到终审 / reject / withdraw）的时间 */
    private LocalDateTime finishedAt;
}
