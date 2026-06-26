package com.company.knowledge.document;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.common.util.RAGFlow;
import com.company.knowledge.document.entity.KnowledgeDoc;
import com.company.knowledge.document.mapper.KnowledgeDocMapper;
import com.company.knowledge.integration.ragflow.DocumentApi;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档业务层。
 *
 * <p>核心增强：上传后自动创建 {@link KnowledgeDoc} 记录（承载审核状态），
 * 列表查询时附带审核状态，形成「上传→审核→发布」闭环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentApi documentApi;
    private final KnowledgeDocMapper knowledgeDocMapper;

    /**
     * 上传文档 → RAGFlow → 自动解析 → 创建 knowledge_doc 记录（DRAFT 状态）。
     */
    public Map<String, Object> upload(String datasetId, String fileName, byte[] bytes, boolean parse) {
        JsonNode uploadResp = documentApi.upload(datasetId, fileName, bytes);
        List<String> docIds = extractDocumentIds(uploadResp);

        if (parse && !docIds.isEmpty()) {
            try {
                documentApi.parse(datasetId, docIds);
            } catch (Exception e) {
                log.warn("auto-parse failed for dataset={}: {}", datasetId, e.getMessage());
            }
        }

        // 为每个上传的文档创建 knowledge_doc 记录
        for (String docId : docIds) {
            createKnowledgeDoc(datasetId, docId, fileName);
        }

        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(uploadResp));
    }

    /**
     * 列出文档，附带审核状态（从 knowledge_doc 表左连）。
     */
    public Map<String, Object> list(String datasetId, int page, int pageSize, String keywords) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 200) pageSize = 30;

        JsonNode resp = documentApi.list(datasetId, page, pageSize, keywords);
        JsonNode data = RAGFlow.extractData(resp);
        List<Map<String, Object>> docs = RAGFlow.toCamelCaseList(data != null ? data.path("docs") : null);

        // 批量查审核状态
        if (!docs.isEmpty()) {
            List<String> docIdList = new ArrayList<>();
            for (Map<String, Object> doc : docs) {
                Object id = doc.get("id");
                if (id != null) docIdList.add(String.valueOf(id));
            }
            if (!docIdList.isEmpty()) {
                Map<String, String> statusMap = queryAuditStatus(docIdList);
                for (Map<String, Object> doc : docs) {
                    String docId = String.valueOf(doc.get("id"));
                    doc.put("auditStatus", statusMap.getOrDefault(docId, "DRAFT"));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docs", docs);
        result.put("total", data != null ? data.path("total").asInt(0) : 0);
        return result;
    }

    public Map<String, Object> delete(String datasetId, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) throw BizException.of(4002, "ids cannot be empty");
        documentApi.delete(datasetId, docIds);
        return Map.of("success", true);
    }

    public Map<String, Object> reparse(String datasetId, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) throw BizException.of(4002, "ids cannot be empty");
        try { documentApi.stopParse(datasetId, docIds); } catch (Exception e) { log.debug("stopParse ignored: {}", e.getMessage()); }
        documentApi.parse(datasetId, docIds);
        return Map.of("success", true);
    }

    public byte[] download(String datasetId, String docId) {
        return documentApi.download(datasetId, docId);
    }

    public String downloadContentType(String datasetId, String docId) {
        return documentApi.downloadContentType(datasetId, docId);
    }

    // ==================== knowledge_doc 管理 ====================

    /**
     * 用 ragflowDocId 查 knowledge_doc 获取本地 id。
     * 如果不存在则自动创建（上传时可能未创建）。
     */
    public Long getOrCreateDocId(String datasetId, String ragflowDocId, String createdBy) {
        if (ragflowDocId == null || ragflowDocId.isEmpty()) {
            throw BizException.of(4002, "ragflowDocId is required");
        }
        try {
            List<Map<String, Object>> existing = knowledgeDocMapper.selectStatusByDocIds(
                    java.util.Collections.singletonList(ragflowDocId));
            if (existing != null && !existing.isEmpty()) {
                // 已存在，查实体拿 id
                KnowledgeDoc doc = knowledgeDocMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getRagflowDocId, ragflowDocId));
                if (doc != null) return doc.getId();
            }
            // 不存在，创建
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setTitle(ragflowDocId);
            doc.setRagflowDocId(ragflowDocId);
            doc.setDatasetId(datasetId);
            doc.setAuditStatus("DRAFT");
            doc.setSecurityLevel(1);
            doc.setSourceType("UPLOAD");
            doc.setCreatedBy(createdBy != null ? createdBy : "system");
            doc.setCreatedAt(java.time.LocalDateTime.now());
            knowledgeDocMapper.insert(doc);
            log.info("[DocumentService] knowledge_doc created on submit: ragflowDocId={}, id={}", ragflowDocId, doc.getId());
            return doc.getId();
        } catch (Exception e) {
            log.error("[DocumentService] getOrCreateDocId failed: {}", e.getMessage(), e);
            throw BizException.of(5003, "Failed to get/create knowledge_doc: " + e.getMessage());
        }
    }
    /** 上传后创建 knowledge_doc 记录（如果不存在） */
    private void createKnowledgeDoc(String datasetId, String ragflowDocId, String title) {
        try {
            // 检查是否已存在
            List<Map<String, Object>> existing = knowledgeDocMapper.selectStatusByDocIds(Collections.singletonList(ragflowDocId));
            if (existing != null && !existing.isEmpty()) return;

            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setTitle(title);
            doc.setRagflowDocId(ragflowDocId);
            doc.setDatasetId(datasetId);
            doc.setAuditStatus("DRAFT");
            doc.setSecurityLevel(1);
            doc.setSourceType("UPLOAD");
            doc.setCreatedBy(getCurrentUser());
            doc.setCreatedAt(LocalDateTime.now());
            knowledgeDocMapper.insert(doc);
            log.info("[DocumentService] knowledge_doc created: ragflowDocId={}, status=DRAFT", ragflowDocId);
        } catch (Exception e) {
            log.warn("[DocumentService] create knowledge_doc failed for {}: {}", ragflowDocId, e.getMessage());
        }
    }

    /** 批量查审核状态，返回 ragflowDocId → auditStatus 映射 */
    private Map<String, String> queryAuditStatus(List<String> docIds) {
        Map<String, String> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = knowledgeDocMapper.selectStatusByDocIds(docIds);
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    String docId = String.valueOf(row.get("ragflow_doc_id"));
                    String status = String.valueOf(row.get("audit_status"));
                    result.put(docId, status);
                }
            }
        } catch (Exception e) {
            log.warn("[DocumentService] queryAuditStatus failed: {}", e.getMessage());
        }
        return result;
    }

    private static String getCurrentUser() {
        try {
            return UserContext.get() != null ? UserContext.get().getPersonId() : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    private static List<String> extractDocumentIds(JsonNode uploadResp) {
        List<String> ids = new ArrayList<>();
        JsonNode data = uploadResp.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                String id = item.path("id").asText("");
                if (!id.isEmpty()) ids.add(id);
            }
        }
        return ids;
    }
}
