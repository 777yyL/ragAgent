package com.company.knowledge.org.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人员副本（来源海康同步，只读）。
 *
 * <p>对应表 {@code sys_person}，由 {@code PersonSyncService} 通过海康
 * {@code /person/personList} 接口同步。
 */
@Data
@TableName("sys_person")
public class SysPerson {

    @TableId
    private String personId;

    private String personName;
    private String orgIndexCode;
    private String orgPath;
    private String orgPathName;
    private String jobNo;
    private String certificateNo;
    private String phone;
    private String email;
    private String company;
    private String post;
    private String postType;

    /** 0 正常；&lt;0 海康侧已删除（软删除） */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 最近一次同步时间 */
    private LocalDateTime syncTime;
}
