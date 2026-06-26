package com.company.knowledge.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核节点操作记录。对应表 {@code audit_node_record}。
 *
 * <p>每次 submit/approve/reject/withdraw 写一条，形成审计轨迹。
 */
@Data
@TableName("audit_node_record")
public class AuditNodeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联实例 ID */
    private Long instanceId;

    /** 节点序号（action 发生时的 currentNode） */
    private Integer nodeOrder;

    /** 操作人 personId */
    private String approverId;

    /** 操作人角色（来自 UserContext） */
    private String approverRole;

    /** 动作：SUBMIT / APPROVE / REJECT / WITHDRAW */
    private String action;

    /** 审批意见 */
    private String comment;

    /** chunk 修改快照（JSONB，可空） */
    private String chunkEdits;

    private LocalDateTime actedAt;
}
