package com.company.knowledge.org.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织副本（来源海康同步，只读）。对应表 {@code sys_org}。
 */
@Data
@TableName("sys_org")
public class SysOrg {

    @TableId
    private String orgIndexCode;

    private String orgName;
    private String orgPath;
    private String parentOrgIndexCode;
    private Boolean isLeaf;
    private Integer sort;
    private Boolean available;
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime syncTime;
}
