package com.company.knowledge.permission.service;

import com.company.knowledge.integration.ragflow.MetadataApi;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档 metadata 批量写入服务。
 *
 * <p>这是「权限模型」写入 RAGFlow 向量库的桥梁：
 * <ul>
 *   <li>Phase 5 {@code PermissionPolicyService} 会按 部门/分类/标签/安全级别
 *       计算策略，然后调用本服务把策略值写入 document.metadata</li>
 *   <li>Phase 3 检索时通过 {@code metadata_condition} 预过滤，实现行级权限</li>
 * </ul>
 *
 * <p>约定写入的 metadata key：
 * <ul>
 *   <li>{@code dept_ids}：可见部门 ID，逗号分隔</li>
 *   <li>{@code categories}：业务分类，逗号分隔</li>
 *   <li>{@code tags}：标签，逗号分隔</li>
 *   <li>{@code security_level}：安全等级（1-4），数字字符串</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataSyncService {

    /** metadata key: 可见部门 ID（逗号分隔） */
    public static final String KEY_DEPT_IDS = "dept_ids";
    /** metadata key: 业务分类（逗号分隔） */
    public static final String KEY_CATEGORIES = "categories";
    /** metadata key: 标签（逗号分隔） */
    public static final String KEY_TAGS = "tags";
    /** metadata key: 安全等级（1-4） */
    public static final String KEY_SECURITY_LEVEL = "security_level";

    private final MetadataApi metadataApi;

    /**
     * 把权限相关 metadata 应用到一批文档上。
     *
     * <p>对每个非空参数会生成一个 {@code updates} 元素，{@code selector} 锁定到
     * 指定的 document_ids。
     *
     * @param datasetId     目标 dataset
     * @param docIds        目标文档 ID 列表（至少 1 个）
     * @param deptIds       可见部门 ID 列表，可为 null/empty 表示不更新
     * @param categories    业务分类列表，可为 null/empty
     * @param tags          标签列表，可为 null/empty
     * @param securityLevel 安全等级（1-4），{@code <=0} 表示不更新
     * @return RAGFlow 响应（含 {@code updated/matched_docs} 字段）
     */
    public JsonNode applyDocMetadata(String datasetId,
                                     List<String> docIds,
                                     List<String> deptIds,
                                     List<String> categories,
                                     List<String> tags,
                                     int securityLevel) {
        if (docIds == null || docIds.isEmpty()) {
            throw com.company.knowledge.common.exception.BizException.of(4002, "docIds cannot be empty");
        }

        List<Map<String, Object>> updates = new ArrayList<>();
        if (deptIds != null && !deptIds.isEmpty()) {
            updates.add(updateItem(KEY_DEPT_IDS, String.join(",", deptIds)));
        }
        if (categories != null && !categories.isEmpty()) {
            updates.add(updateItem(KEY_CATEGORIES, String.join(",", categories)));
        }
        if (tags != null && !tags.isEmpty()) {
            updates.add(updateItem(KEY_TAGS, String.join(",", tags)));
        }
        if (securityLevel > 0) {
            updates.add(updateItem(KEY_SECURITY_LEVEL, String.valueOf(securityLevel)));
        }

        if (updates.isEmpty()) {
            log.debug("applyDocMetadata no fields to update, skip. dataset={} docs={}", datasetId, docIds);
            Map<String, Object> empty = new HashMap<>();
            empty.put("code", 0);
            empty.put("data", Map.of("updated", 0, "matched_docs", docIds.size()));
            return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(empty);
        }

        Map<String, Object> selector = new HashMap<>();
        selector.put("document_ids", docIds);

        return metadataApi.update(datasetId, selector, updates, null);
    }

    /**
     * 删除文档上指定的 metadata key（用于权限回收场景）。
     */
    public JsonNode deleteDocMetadata(String datasetId,
                                      List<String> docIds,
                                      List<String> keys) {
        if (docIds == null || docIds.isEmpty()) {
            throw com.company.knowledge.common.exception.BizException.of(4002, "docIds cannot be empty");
        }
        if (keys == null || keys.isEmpty()) {
            throw com.company.knowledge.common.exception.BizException.of(4002, "keys cannot be empty");
        }
        List<Map<String, Object>> deletes = new ArrayList<>();
        for (String key : keys) {
            Map<String, Object> d = new HashMap<>();
            d.put("key", key);
            deletes.add(d);
        }
        Map<String, Object> selector = new HashMap<>();
        selector.put("document_ids", docIds);
        return metadataApi.update(datasetId, selector, null, deletes);
    }

    private static Map<String, Object> updateItem(String key, String value) {
        Map<String, Object> item = new HashMap<>();
        item.put("key", key);
        item.put("value", value);
        return item;
    }
}
