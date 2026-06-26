package com.company.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 海康平台对接配置。对应 application.yml 的 {@code hikvision.*} 前缀。
 *
 * <p>同时承担两种用途：
 * <ul>
 *   <li>{@link #appKey} / {@link #appSecret}：Artemis 应用签名（后端调用组织/人员接口）</li>
 *   <li>{@link #appKey}：OAuth2 client_id（用户 SSO 跳转）</li>
 *   <li>{@link #oauthRedirectUri}：OAuth2 回调地址</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "hikvision")
public class HikvisionProperties {

    /** 海康平台 host:port，如 {@code 10.33.25.18:443} */
    private String host;

    /** API 网关注册的 appKey（同时是 OAuth2 client_id） */
    private String appKey;

    /** API 网关注册的 appSecret（Artemis HMAC 签名密钥） */
    private String appSecret;

    /** OAuth2 回调地址，如 {@code https://knowledge.company.com/sso/callback} */
    private String oauthRedirectUri;
}
