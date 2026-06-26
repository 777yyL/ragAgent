package com.company.knowledge.integration.hikvision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 海康人员 DTO。对应 {@code /api/resource/v2/person/personList} 返回结构。
 *
 * <p>注意：海康字段是 camelCase，与本地实体 {@code SysPerson} 的下划线命名不同，
 * 同步时由 {@code PersonSyncService} 做字段映射。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HikPerson {

    private String personId;
    private String personName;

    /** 所属组织唯一标识码（外键，对应 {@code SysOrg.orgIndexCode}） */
    private String orgIndexCode;

    /** 组织层级路径，如 {@code @root000000@xxx@} */
    private String orgPath;

    /** 组织层级名称路径，如 {@code @默认部门@xx处@} */
    private String orgPathName;

    private String jobNo;
    private Integer certificateType;
    private String certificateNo;
    private String phoneNo;
    private String email;
    private String company;

    @JsonProperty("employeePost")
    private String post;

    @JsonProperty("postType")
    private String postType;

    private String createTime;
    private String updateTime;

    /** 状态：小于 0 表示已被删除（海康软删除标记） */
    private Integer status;
}
