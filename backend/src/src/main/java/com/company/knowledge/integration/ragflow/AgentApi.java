package com.company.knowledge.integration.ragflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * RAGFlow {@code AGENT MANAGEMENT} 接口包装。
 *
 * <p>覆盖：
 * <ul>
 *   <li>Agent CRUD：{@code GET/POST/PUT/DELETE /api/v1/agents}</li>
 *   <li>Agent 会话：{@code POST /api/v1/agents/{agentId}/sessions}</li>
 *   <li>Agent 对话：{@code POST /api/v1/agents/{agentId}/completions}</li>
 * </ul>
 *
 * <p>说明：对话端点 {@code /api/v1/agents/{agentId}/completions} 虽被 RAGFlow 标记 deprecated，
 * 但 plan（Task 6.1）指定使用该路径。新端点 {@code /api/v1/agents/chat/completions} 可在后续替换。
 */
@Component
@RequiredArgsConstructor
public class AgentApi {

    private final RAGFlowClient client;

    /**
     * 列出 agent。所有参数均可为 null。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param id       精确 ID 过滤
     * @param title    精确 title 过滤
     */
    public JsonNode listAgents(Integer page, Integer pageSize, String id, String title) {
        Map<String, Object> params = new HashMap<>();
        if (page != null) {
            params.put("page", page);
        }
        if (pageSize != null) {
            params.put("page_size", pageSize);
        }
        if (id != null && !id.isEmpty()) {
            params.put("id", id);
        }
        if (title != null && !title.isEmpty()) {
            params.put("title", title);
        }
        return client.get("/api/v1/agents", params);
    }

    /**
     * 列出全部 agent（默认分页）。
     */
    public JsonNode listAgents() {
        return listAgents(null, null, null, null);
    }

    /**
     * 创建 agent。
     *
     * @param body 含 title(必填)/description/dsl
     */
    public JsonNode createAgent(Map<String, Object> body) {
        return client.post("/api/v1/agents", body);
    }

    /**
     * 更新 agent（partial update，仅传入字段会被更新）。
     */
    public JsonNode updateAgent(String id, Map<String, Object> body) {
        return client.put("/api/v1/agents/" + id, body);
    }

    /**
     * 删除 agent。
     */
    public JsonNode deleteAgent(String id) {
        // RAGFlow DELETE /api/v1/agents/{id} 要求带 body（可为空对象）
        return client.deleteWithBody("/api/v1/agents/" + id, new HashMap<>());
    }

    /**
     * 创建 agent session。
     *
     * <p>对应 RAGFlow {@code POST /api/v1/agents/{agentId}/sessions}。
     *
     * @param agentId agent ID
     * @param body    Begin 组件所需参数（无则传空对象）
     */
    public JsonNode createAgentSession(String agentId, Map<String, Object> body) {
        if (body == null) {
            body = new HashMap<>();
        }
        return client.post("/api/v1/agents/" + agentId + "/sessions", body);
    }

    /**
     * 与 agent 对话。
     *
     * <p>对应 RAGFlow {@code POST /api/v1/agents/{agentId}/completions}。
     *
     * @param agentId   agent ID
     * @param sessionId session ID（可为 null，RAGFlow 会自动创建）
     * @param body      额外字段，如 question/query/stream/inputs 等；本方法会自动注入 agent_id/session_id
     */
    public JsonNode converseWithAgent(String agentId, String sessionId, Map<String, Object> body) {
        if (body == null) {
            body = new HashMap<>();
        }
        body.putIfAbsent("agent_id", agentId);
        if (sessionId != null && !sessionId.isEmpty()) {
            body.putIfAbsent("session_id", sessionId);
        }
        body.putIfAbsent("stream", false);
        return client.post("/api/v1/agents/" + agentId + "/completions", body);
    }
}
