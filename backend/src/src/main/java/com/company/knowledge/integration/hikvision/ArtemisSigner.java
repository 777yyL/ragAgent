package com.company.knowledge.integration.hikvision;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;

/**
 * 海康 Artemis API 网关签名计算（HMAC-SHA256）。
 *
 * <p>对应《海康平台 Oauth2 服务接口说明书》§调用认证 与《Artemis 网关 API 签名规范》。
 *
 * <p>签名串拼接顺序（每段以 {@code \n} 分隔）：
 * <pre>
 *   {method}\n
 *   {accept}\n
 *   {content-md5}\n
 *   {content-type}\n
 *   {date}\n
 *   {headers}\n          (本实现暂不用自定义 header，留空)
 *   {url}?{sorted-query} (query 按 key 字典序排列，无 query 时不含 ?)
 * </pre>
 *
 * <p>注意：本类不依赖 Spring，可独立单元测试；密钥由调用方（ArtemisClient）注入。
 */
public final class ArtemisSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final byte[] secretBytes;

    public ArtemisSigner(String appSecret) {
        this.secretBytes = (appSecret == null ? "" : appSecret).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 计算签名（小写 hex）。
     *
     * @param method        HTTP 方法（GET/POST/PUT/DELETE，大写）
     * @param accept        Accept 头（通常 application/json）
     * @param contentMd5    Body 的 MD5 Base64（无 body 时为空串）
     * @param contentType   Content-Type 头
     * @param date          Date 头（可空）
     * @param url           URI path（不含 host 与 query），如 /api/resource/v2/person/personList
     * @param sortedQuery   已按 key 排序的 query 参数；可为空或 null
     * @return 64 字符的小写 hex 签名
     */
    public String sign(String method, String accept, String contentMd5,
                       String contentType, String date,
                       String url, SortedMap<String, String> sortedQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append('\n');
        sb.append(nullToEmpty(accept)).append('\n');
        sb.append(nullToEmpty(contentMd5)).append('\n');
        sb.append(nullToEmpty(contentType)).append('\n');
        sb.append(nullToEmpty(date)).append('\n');
        // 自定义 headers 段本实现留空（仅一行换行已在前）
        sb.append(nullToEmpty(url));
        if (sortedQuery != null && !sortedQuery.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : sortedQuery.entrySet()) {
                if (!first) sb.append('&');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA256));
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC sign failed", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
