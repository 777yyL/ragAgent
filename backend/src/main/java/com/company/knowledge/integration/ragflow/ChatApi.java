package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * RAGFlow {@code CHAT ASSISTANT MANAGEMENT} + {@code SESSION MANAGEMENT} 接口包装。
 *
 * <p>覆盖：
 * <ul>
 *   <li>助手 CRUD：{@code POST/PUT/GET/DELETE /api/v1/chats}</li>
 *   <li>会话管理：{@code POST/GET/DELETE /api/v1/chats/{chatId}/sessions}</li>
 *   <li>对话补全：{@code POST /api/v1/chats/{chatId}/completions}</li>
 *   <li>消息反馈：{@code PUT /api/v1/chats/{chatId}/sessions/{sessionId}/messages/{msgId}/feedback}</li>
 * </ul>
 *
 * <p>说明：对话端点 {@code /api/v1/chats/{chatId}/completions} 虽被 RAGFlow 标记 deprecated，
 * 但 plan（Task 6.1）指定使用该路径以保持与现有 UI 集成一致；后端可通过此包装统一替换为新端点。
 */
@Component
@RequiredArgsConstructor
public class ChatApi {

    private final RAGFlowClient client;

    // ========================= Chat Assistant =========================

    /**
     * 创建 chat assistant。
     *
     * @param body 含 name/dataset_ids/llm_id/llm_setting/prompt_config 等
     */
    public JsonNode createChatAssistant(Map<String, Object> body) {
        return client.post("/api/v1/chats", body);
    }

    /**
     * 更新 chat assistant（全量覆盖，省略字段重置为默认值）。
     * 对应 PUT /api/v1/chats/{chat_id}
     */
    public JsonNode updateChat(String id, Map<String, Object> body) {
        return client.put("/api/v1/chats/" + id, body);
    }

    /**
     * 部分更新 chat assistant（未指定字段保留原值）。
     * 对应 PATCH /api/v1/chats/{chat_id}
     */
    public JsonNode patchChat(String id, Map<String, Object> body) {
        return client.patch("/api/v1/chats/" + id, body);
    }

    /**
     * 批量删除 chat assistant（新版端点，body 带 ids）。
     * 对应 DELETE /api/v1/chats
     */
    public JsonNode batchDeleteAssistants(java.util.List<String> ids) {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", ids != null ? ids : java.util.Collections.emptyList());
        return client.deleteWithBody("/api/v1/chats", body);
    }

    /**
     * 列出 chat assistant。所有参数均可为 null。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param keywords 名称模糊匹配
     * @param id       精确 ID 过滤
     * @param name     精确名称过滤
     */
    public JsonNode listChatAssistants(Integer page, Integer pageSize, String keywords, String id, String name) {
        Map<String, Object> params = new HashMap<>();
        if (page != null) {
            params.put("page", page);
        }
        if (pageSize != null) {
            params.put("page_size", pageSize);
        }
        if (keywords != null && !keywords.isEmpty()) {
            params.put("keywords", keywords);
        }
        if (id != null && !id.isEmpty()) {
            params.put("id", id);
        }
        if (name != null && !name.isEmpty()) {
            params.put("name", name);
        }
        return client.get("/api/v1/chats", params);
    }

    /**
     * 列出全部 chat assistant（使用默认分页）。
     */
    public JsonNode listChatAssistants() {
        return listChatAssistants(null, null, null, null, null);
    }

    /**
     * 获取单个 chat assistant。
     */
    public JsonNode getChatAssistant(String id) {
        return client.get("/api/v1/chats/" + id, null);
    }

    /**
     * 删除单个 chat assistant。
     */
    public JsonNode deleteChatAssistant(String id) {
        return client.delete("/api/v1/chats/" + id, null);
    }

    // ========================= Session =========================

    /**
     * 创建 session。
     *
     * @param chatId 关联的 chat assistant ID
     * @param body   含 name（可选 user_id）
     */
    public JsonNode createSession(String chatId, Map<String, Object> body) {
        if (body == null) {
            body = new HashMap<>();
        }
        return client.post("/api/v1/chats/" + chatId + "/sessions", body);
    }

    /**
     * 列出 session。
     */
    public JsonNode listSessions(String chatId, Integer page, Integer pageSize) {
        Map<String, Object> params = new HashMap<>();
        if (page != null) {
            params.put("page", page);
        }
        if (pageSize != null) {
            params.put("page_size", pageSize);
        }
        return client.get("/api/v1/chats/" + chatId + "/sessions", params);
    }

    /**
     * 列出全部 session（默认分页）。
     */
    public JsonNode listSessions(String chatId) {
        return listSessions(chatId, null, null);
    }

    /**
     * 删除 session。RAGFlow 接口要求传 body {@code {"ids":[...]}} 或 {@code {"delete_all":true}}。
     *
     * @param chatId    关联的 chat assistant ID
     * @param sessionId 要删除的 session ID
     */
    public JsonNode deleteSession(String chatId, String sessionId) {
        Map<String, Object> body = new HashMap<>();
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add(sessionId);
        body.put("ids", ids);
        return client.deleteWithBody("/api/v1/chats/" + chatId + "/sessions", body);
    }

    /**
     * 批量删除 session。
     */
    public JsonNode deleteSessions(String chatId, java.util.List<String> sessionIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", sessionIds != null ? sessionIds : java.util.Collections.emptyList());
        return client.deleteWithBody("/api/v1/chats/" + chatId + "/sessions", body);
    }

    // ========================= Completion =========================

    /**
     * 与 chat assistant 对话（非流式建议 stream=false）。
     *
     * <p>使用新端点 {@code POST /api/v1/chat/completions}（旧端点 /chats/{id}/completions 已废弃）。
     * 三种模式：
     * <ul>
     *   <li>无 chat_id：直接用租户默认聊天模型</li>
     *   <li>有 chat_id 无 session_id：使用该助手配置并自动创建新会话</li>
     *   <li>有 chat_id + session_id：继续现有会话</li>
     * </ul>
     *
     * @param chatId    chat assistant ID（可选）
     * @param sessionId 会话 ID（可选）
     * @param body      含 question/messages/stream 等
     */
    public JsonNode converse(String chatId, String sessionId, Map<String, Object> body) {
        if (body == null) {
            body = new HashMap<>();
        }
        if (chatId != null && !chatId.isEmpty()) {
            body.putIfAbsent("chat_id", chatId);
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            body.putIfAbsent("session_id", sessionId);
        }
        body.putIfAbsent("stream", false);
        // 新端点：POST /api/v1/chat/completions（不在路径里带 chatId）
        return client.post("/api/v1/chat/completions", body);
    }

    // ========================= Feedback =========================

    /**
     * 更新消息反馈（点赞/踩）。
     *
     * <p>对应 RAGFlow {@code PUT /api/v1/chats/{chatId}/sessions/{sessionId}/messages/{msgId}/feedback}。
     *
     * @param feedback 包含 thumbup(boolean)、feedback(string, 可选)
     */
    public JsonNode updateMessageFeedback(String chatId, String sessionId, String messageId, Map<String, Object> feedback) {
        if (feedback == null) {
            feedback = new HashMap<>();
        }
        return client.put(
                "/api/v1/chats/" + chatId + "/sessions/" + sessionId + "/messages/" + messageId + "/feedback",
                feedback);
    }
}
