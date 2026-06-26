package com.company.knowledge.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务文档元数据。与 RAGFlow document 一一对应，承载审核状态。
 *
 * <p>审核状态流转：DRAFT → PENDING → APPROVED → PUBLISHED
 *                  ↓         ↓
 *               WITHDRAWN  REJECTED
 */
@Data
@TableName("knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String ragflowDocId;
    private String datasetId;
    private String businessType;
    private String deptId;
    private String[] tags;
    private Integer securityLevel;
    private Long versionId;

    /** DRAFT / PENDING / APPROVED / REJECTED / WITHDRAWN / PUBLISHED */
    private String auditStatus;

    private String sourceType;
    private String sourceRef;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
