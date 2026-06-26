package com.company.knowledge.integration.hikvision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OAuth2 token 接口返回的 data 结构。对应海康 /oauth2/token 的 data 字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    /** 有效期（秒），海康固定 1800（30 分钟） */
    @JsonProperty("expires_in")
    private Integer expiresIn;
}
