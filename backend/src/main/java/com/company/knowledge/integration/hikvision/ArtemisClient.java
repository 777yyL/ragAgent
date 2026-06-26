package com.company.knowledge.integration.hikvision;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.config.HikvisionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 海康 Artemis API 网关通用 HTTP 客户端。
 *
 * <p>每次请求自动：
 * <ol>
 *   <li>计算 body 的 MD5 Base64（content-md5）</li>
 *   <li>用 {@link ArtemisSigner} 计算 X-CA-Signature</li>
 *   <li>加 X-CA-Key / X-CA-Signature / Accept / Content-Type 头</li>
 *   <li>解析响应，code != 0 抛 {@link BizException}(6001)</li>
 * </ol>
 *
 * <p>异常码：6001 海康业务错；6002 网络错。
 */
@Slf4j
@Component
public class ArtemisClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final HikvisionProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = com.company.knowledge.integration.hikvision.HttpClientFactory.createTrustAllClient();

    public ArtemisClient(HikvisionProperties props) {
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(String uri, Object body, Map<String, String> query) {
        return (Map<String, Object>) request("POST", uri, body, query);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String uri, Map<String, String> query) {
        return (Map<String, Object>) request("GET", uri, null, query);
    }

    private Object request(String method, String uri, Object body, Map<String, String> query) {
        try {
            String jsonBody = body != null ? mapper.writeValueAsString(body) : "";
            String contentMd5 = body != null ? md5Base64(jsonBody) : "";
            TreeMap<String, String> sortedQuery = query != null ? new TreeMap<>(query) : new TreeMap<>();

            ArtemisSigner signer = new ArtemisSigner(props.getAppSecret());
            String signature = signer.sign(method, "application/json", contentMd5,
                    "application/json", "", uri, sortedQuery);

            HttpUrl.Builder urlBuilder = HttpUrl.parse("https://" + props.getHost() + uri).newBuilder();
            if (query != null) query.forEach(urlBuilder::addQueryParameter);

            Request.Builder rb = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("X-CA-Key", props.getAppKey())
                    .header("X-CA-Signature", signature)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");

            if (body != null) {
                rb.method(method, RequestBody.create(jsonBody, JSON));
            } else {
                rb.method(method, null);
            }

            try (Response resp = http.newCall(rb.build()).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "{}";
                log.debug("Artemis {} {} -> {} {}", method, uri, resp.code(), abbreviate(respStr, 200));
                Object parsed = mapper.readValue(respStr, Object.class);
                if (parsed instanceof Map) {
                    Map<String, Object> node = (Map<String, Object>) parsed;
                    if (!"0".equals(String.valueOf(node.get("code")))) {
                        throw BizException.of(6001, "Hikvision error: " + node.get("msg"));
                    }
                    return node;
                }
                throw BizException.of(6001, "Hikvision unexpected response: " + respStr);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Artemis {} {} failed: {}", method, uri, e.getMessage());
            throw BizException.of(6002, "Hikvision network error: " + e.getMessage(), e);
        }
    }

    private static String md5Base64(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 failed", e);
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
