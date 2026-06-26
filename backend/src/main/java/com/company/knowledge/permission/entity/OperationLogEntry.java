package com.company.knowledge.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志（等保三级）。对应表 {@code operation_log}。
 *
 * <p>由 {@code OperationLogAspect} 在 {@code @OperationLog} 标注的方法执行后
 * 异步写入。before/after snapshot 为 JSON 字符串（TEXT 列）。
 *
 * <p>类名加 {@code Entry} 后缀，避免与 {@code @OperationLog} 注解同名导致 import 冲突。
 */
@Data
@TableName("operation_log")
public class OperationLogEntry {

    /** 主键，BIGSERIAL */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 personId */
    private String userId;

    /** 操作人显示名（从 UserContext） */
    private String username;

    /** 动作，来自 {@code @OperationLog.action} */
    private String action;

    /** 资源类型（如 DOC/CATEGORY/DATASET），可选 */
    private String resourceType;

    /** 资源 ID，可选 */
    private String resourceId;

    /** 请求来源 IP */
    private String requestIp;

    /** 请求体 JSON（如方法入参摘要） */
    private String requestBody;

    /** 执行前快照 JSON */
    private String beforeSnapshot;

    /** 执行后快照 JSON */
    private String afterSnapshot;

    /** 结果：SUCCESS / FAIL */
    private String result;

    /** 失败时的错误消息 */
    private String errorMsg;

    private LocalDateTime createdAt;
}
