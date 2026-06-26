package com.company.knowledge.integration.hikvision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 海康组织 DTO。对应 {@code /api/resource/v2/org/*} 返回结构。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HikOrg {

    private String orgIndexCode;
    private String orgName;
    private String orgPath;
    private String parentOrgIndexCode;
    private Boolean leaf;
    private Integer sort;
    private Boolean available;

    /** 小于 0 表示已被删除 */
    private Integer status;

    private String createTime;
    private String updateTime;
}
