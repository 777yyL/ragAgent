package com.company.knowledge.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * RAGFlow 响应解析工具。
 *
 * <p>RAGFlow 所有接口返回 {@code {code:0, msg:..., data:..., total/total_xxx:N}}。
 * 本工具负责：
 * <ol>
 *   <li>{@link #extractData} 提取 data 部分（可能是对象、数组或 null）</li>
 *   <li>{@link #toCamelCaseMap} 把 snake_case 的 JsonNode 转成 camelCase 的 Map</li>
 *   <li>{@link #toCamelCaseList} 数组版本</li>
 * </ol>
 *
 * <p>用法：
 * <pre>{@code
 * JsonNode resp = documentApi.list(datasetId, page, pageSize);
 * JsonNode data = RAGFlow.extractData(resp);
 * List<Map<String,Object>> docs = RAGFlow.toCamelCaseList(data.path("docs"));
 * int total = data.path("total").asInt(0);
 * }</pre>
 */
public final class RAGFlow {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RAGFlow() {}

    /** 从 RAGFlow 完整响应中提取 {@code data} 字段。 */
    public static JsonNode extractData(JsonNode response) {
        if (response == null) return null;
        JsonNode data = response.path("data");
        return data.isMissingNode() ? null : data;
    }

    /**
     * JsonNode → Map，递归把 snake_case key 转 camelCase。
     * 支持 Map / List / 标量的任意嵌套。
     */
    public static Map<String, Object> toCamelCaseMap(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = MAPPER.treeToValue(node, Map.class);
            return convertKeys(raw);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * JsonNode 数组 → List&lt;Map&gt;，每个元素 snake→camel。
     */
    public static List<Map<String, Object>> toCamelCaseList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) return Collections.emptyList();
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            list.add(toCamelCaseMap(item));
        }
        return list;
    }

    /**
     * JsonNode → Object，自动判断类型（Map / List / 标量）。
     */
    public static Object toCamelCase(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) return toCamelCaseList(node);
        return toCamelCaseMap(node);
    }

    // ==================== 内部转换 ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertKeys(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (map == null) return result;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            result.put(snakeToCamel(e.getKey()), convertValue(e.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(Object value) {
        if (value instanceof Map) return convertKeys((Map<String, Object>) value);
        if (value instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object item : (List<?>) value) list.add(convertValue(item));
            return list;
        }
        return value;
    }

    static String snakeToCamel(String snake) {
        if (snake == null || snake.indexOf('_') < 0) return snake;
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upper = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') { upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }
}
