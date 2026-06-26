package com.company.knowledge.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 审核发现的问题。对应表 {@code ai_audit_issue}。
 *
 * <p>每条记录表示一次检测在某 chunk 上发现的问题，
 * 由 {@link com.company.knowledge.audit.service.AiAuditService} 写入。
 */
@Data
@TableName("ai_audit_issue")
public class AiAuditIssue {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联文档 ID */
    private Long docId;

    /** 关联 chunk ID（来自 RAGFlow） */
    private String chunkId;

    /** 问题类型，见 {@link com.company.knowledge.audit.constant.IssueType} */
    private String issueType;

    /** 严重等级：INFO / WARN / ERROR / CRITICAL */
    private String severity;

    /** 位置描述（chunk 内的文字片段或行号） */
    private String position;

    /** 问题描述 */
    private String description;

    /** 修改建议 */
    private String suggestion;

    /** 处理状态：OPEN / RESOLVED / IGNORED */
    private String status;

    private LocalDateTime createdAt;
}
