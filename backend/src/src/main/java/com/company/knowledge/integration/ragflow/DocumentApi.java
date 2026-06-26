package com.company.knowledge.integration.ragflow;

import com.company.knowledge.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAGFlow {@code FILE MANAGEMENT WITHIN DATASET} 接口包装。
 *
 * <p>覆盖上传/更新/下载/列表/删除/解析/停止解析共 7 个端点。上传使用 OkHttp
 * {@link MultipartBody}，以流式方式发送字节，避免 base64 体积膨胀。
 */
@Component
@RequiredArgsConstructor
public class DocumentApi {

    private final RAGFlowClient client;

    private static final MediaType OCTET = MediaType.parse("application/octet-stream");

    /**
     * 上传本地文件到指定 dataset。
     *
     * @param datasetId 目标 dataset ID
     * @param fileName  保存的文件名
     * @param bytes     文件字节内容
     * @return RAGFlow 响应（含每条 document 的 id/run 等）
     */
    public JsonNode upload(String datasetId, String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw BizException.of(4002, "file content is empty");
        }
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, RequestBody.create(bytes, OCTET))
                .build();
        return client.postMultipart("/api/v1/datasets/" + datasetId + "/documents", body);
    }

    /**
     * 更新文档配置（name/meta_fields/chunk_method/parser_config/enabled）。
     *
     * @param datasetId dataset ID
     * @param docId     文档 ID
     * @param body      更新字段
     */
    public JsonNode update(String datasetId, String docId, Map<String, Object> body) {
        return client.put("/api/v1/datasets/" + datasetId + "/documents/" + docId, body);
    }

    /**
     * 下载文档原始字节。
     *
     * @param datasetId dataset ID
     * @param docId     文档 ID
     * @return 文件字节（调用方据此写入 HttpServletResponse）
     */
    public byte[] download(String datasetId, String docId) {
        return client.getBytes("/api/v1/datasets/" + datasetId + "/documents/" + docId);
    }

    /**
     * 获取下载响应的 Content-Type（不重复下载业务内容，仅探测头）。
     */
    public String downloadContentType(String datasetId, String docId) {
        return client.getContentType("/api/v1/datasets/" + datasetId + "/documents/" + docId);
    }

    /**
     * 列出 dataset 中的文档。
     *
     * @param datasetId dataset ID
     * @param page      页码（从 1 开始）
     * @param pageSize  每页条数
     * @param keywords  名称模糊匹配，可为 null
     */
    public JsonNode list(String datasetId, int page, int pageSize, String keywords) {
        Map<String, Object> params = new HashMap<>();
        params.put("dataset_id", datasetId);
        params.put("page", page);
        params.put("page_size", pageSize);
        if (keywords != null && !keywords.isEmpty()) {
            params.put("keywords", keywords);
        }
        return client.get("/api/v1/datasets/" + datasetId + "/documents", params);
    }

    /**
     * 按 ID 删除文档。
     *
     * @param datasetId dataset ID
     * @param docIds    待删除文档 ID 列表
     */
    public JsonNode delete(String datasetId, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            throw BizException.of(4002, "ids cannot be empty");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("ids", docIds);
        // RAGFlow DELETE 需要 JSON body，用 RAGFlowClient.delete(Map params) 无法满足，
        // 故通过 post 通道转交。实际为 DELETE /documents 带 JSON body。
        return client.deleteWithBody("/api/v1/datasets/" + datasetId + "/documents", body);
    }

    /**
     * 触发文档解析（POST /chunks with document_ids）。
     */
    public JsonNode parse(String datasetId, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            throw BizException.of(4002, "document_ids cannot be empty");
        }
        return client.post("/api/v1/datasets/" + datasetId + "/chunks",
                Map.of("document_ids", docIds));
    }

    /**
     * 停止文档解析（DELETE /chunks with document_ids）。
     */
    public JsonNode stopParse(String datasetId, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            throw BizException.of(4002, "document_ids cannot be empty");
        }
        return client.deleteWithBody("/api/v1/datasets/" + datasetId + "/chunks",
                Map.of("document_ids", docIds));
    }
}
