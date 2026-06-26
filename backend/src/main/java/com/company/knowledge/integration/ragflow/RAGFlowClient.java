package com.company.knowledge.integration.ragflow;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.config.RAGFlowProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * RAGFlow HTTP 通用客户端。
 *
 * <p>所有 RAGFlow API 模块（Dataset/Document/Chunk/Retrieval 等）基于此封装：
 * <ul>
 *   <li>统一加 {@code Authorization: Bearer <api-key>}</li>
 *   <li>统一解析响应、把 {@code code != 0} 转成 {@link BizException}</li>
 *   <li>统一异常：5001 RAGFlow 业务错误；5002 网络错误</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RAGFlowClient {

    private final RAGFlowProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public JsonNode get(String path, Map<String, ?> params) {
        return request("GET", path, params, null);
    }

    public JsonNode post(String path, Object body) {
        return request("POST", path, null, body);
    }

    public JsonNode put(String path, Object body) {
        return request("PUT", path, null, body);
    }

    public JsonNode delete(String path, Map<String, ?> params) {
        return request("DELETE", path, params, null);
    }

    /**
     * 发送带 JSON body 的 DELETE 请求（RAGFlow 部分接口如删除文档需要）。
     */
    public JsonNode deleteWithBody(String path, Object body) {
        return request("DELETE", path, null, body);
    }

    public JsonNode patch(String path, Object body) {
        return request("PATCH", path, null, body);
    }

    /**
     * 发送 multipart/form-data 请求（用于文件上传）。
     *
     * @param path    API 路径（不以 /api/v1 开头时由调用方自行拼好）
     * @param builder 已构造好的 MultipartBody
     * @return 解析后的 JSON
     */
    public JsonNode postMultipart(String path, RequestBody builder) {
        try {
            Request req = new Request.Builder()
                    .url(props.getBaseUrl() + path)
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .post(builder)
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "{}";
                log.debug("RAGFlow POST(multipart) {} -> {} {}", path, resp.code(), abbreviate(respStr, 200));
                JsonNode node = mapper.readTree(respStr);
                int code = node.path("code").asInt(-1);
                if (code != 0) {
                    throw BizException.of(5001, "RAGFlow error: " + node.path("msg").asText(respStr));
                }
                return node;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAGFlow multipart {} failed: {}", path, e.getMessage());
            throw BizException.of(5002, "RAGFlow network error: " + e.getMessage());
        }
    }

    /**
     * 下载二进制内容（如文档下载），返回原始字节。
     */
    public byte[] getBytes(String path) {
        try {
            Request req = new Request.Builder()
                    .url(props.getBaseUrl() + path)
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .get()
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    throw BizException.of(5001, "RAGFlow download failed: HTTP " + resp.code());
                }
                return resp.body() != null ? resp.body().bytes() : new byte[0];
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAGFlow getBytes {} failed: {}", path, e.getMessage());
            throw BizException.of(5002, "RAGFlow network error: " + e.getMessage());
        }
    }

    /** 下载响应的 Content-Type（用于设置响应头）。 */
    public String getContentType(String path) {
        try {
            Request req = new Request.Builder()
                    .url(props.getBaseUrl() + path)
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .get()
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                MediaType mt = resp.body() != null ? resp.body().contentType() : null;
                return mt != null ? mt.toString() : "application/octet-stream";
            }
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private JsonNode request(String method, String path, Map<String, ?> params, Object body) {
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(props.getBaseUrl() + path).newBuilder();
            if (params != null) {
                params.forEach((k, v) -> {
                    if (v != null) urlBuilder.addQueryParameter(k, String.valueOf(v));
                });
            }

            Request.Builder rb = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "Bearer " + props.getApiKey());

            if (body != null) {
                String json = mapper.writeValueAsString(body);
                rb.method(method, RequestBody.create(json, MediaType.parse("application/json")));
            } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
                // RAGFlow 要求所有非 GET 请求都带 Content-Type: application/json
                rb.method(method, RequestBody.create("{}", MediaType.parse("application/json")));
            } else {
                rb.method(method, null);
            }

            try (Response resp = http.newCall(rb.build()).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "{}";
                log.debug("RAGFlow {} {} -> {} {}", method, path, resp.code(), abbreviate(respStr, 200));
                JsonNode node = mapper.readTree(respStr);
                int code = node.path("code").asInt(-1);
                if (code != 0) {
                    String msg = node.path("msg").asText(respStr);
                    throw BizException.of(5001, "RAGFlow error: " + msg);
                }
                return node;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAGFlow {} {} failed: {}", method, path, e.getMessage());
            throw BizException.of(5002, "RAGFlow network error: " + e.getMessage());
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
