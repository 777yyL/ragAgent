package com.company.knowledge.search.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.knowledge.permission.handler.StringArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检索埋点记录，对应 {@code search_log} 表。
 */
@Data
@TableName(value = "search_log", autoResultMap = true)
public class SearchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String query;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] datasetIds;

    private Integer resultCount;

    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] topChunkIds;

    private Integer responseMs;

    @TableField(exist = false)
    private String type;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
