package com.company.knowledge.agent;

import com.company.knowledge.agent.dto.CreateAgentRequest;
import com.company.knowledge.agent.dto.UpdateAgentRequest;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.common.util.RAGFlow;
import com.company.knowledge.integration.ragflow.AgentApi;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 业务层。封装 {@link AgentApi}，做参数校验与字段映射。
 *
 * <p>典型流程：
 * <ol>
 *   <li>{@link #list} 透传到 {@link AgentApi#listAgents()}</li>
 *   <li>{@link #get} 从 list 过滤单条（RAGFlow 无独立 get agent 接口时）</li>
 *   <li>{@link #create}/{@link #update} 将 DTO 的 canvasConfig 映射到 RAGFlow 的 {@code dsl} 字段</li>
 *   <li>{@link #delete} 透传</li>
 * </ol>
 *
 * <p>对应 Phase 6 Task 6.2，覆盖 7 大智能体（汽机/锅炉/电气/环保/燃料/安全/综合）的 Agent 配置管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentApi agentApi;

    /**
     * 列出全部 agent。
     *
     * <p>RAGFlow {@code /api/v1/agents} 返回 {@code data} 可能是：
     * <ul>
     *   <li>数组：直接是 agent 列表</li>
     *   <li>对象：含 {@code items/total} 等字段（旧版本兼容）</li>
     * </ul>
     * 这里统一返回扁平的 camelCase List。
     */
    public List<Map<String, Object>> list() {
        JsonNode resp = agentApi.listAgents();
        JsonNode data = RAGFlow.extractData(resp);
        if (data == null) {
            return java.util.Collections.emptyList();
        }
        // data 是数组：直接转
        if (data.isArray()) {
            return RAGFlow.toCamelCaseList(data);
        }
        // data 是对象，尝试取常见数组字段
        if (data.isObject()) {
            JsonNode items = data.path("items");
            if (items.isArray()) {
                return RAGFlow.toCamelCaseList(items);
            }
            JsonNode agents = data.path("agents");
            if (agents.isArray()) {
                return RAGFlow.toCamelCaseList(agents);
            }
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 获取单个 agent。
     *
     * <p>RAGFlow 没有专门的 {@code GET /api/v1/agents/{id}}，使用 {@code listAgents(id=...)} 过滤。
     */
    public Map<String, Object> get(String id) {
        if (id == null || id.isEmpty()) {
            throw BizException.of(4002, "id is required");
        }
        JsonNode resp = agentApi.listAgents(null, null, id, null);
        JsonNode data = RAGFlow.extractData(resp);
        if (data == null) {
            throw BizException.of(4040, "agent not found: " + id);
        }
        // list 接口返回数组
        if (data.isArray() && data.size() > 0) {
            return RAGFlow.toCamelCaseMap(data.get(0));
        }
        // 兼容：某些版本可能返回对象
        if (data.isObject() && data.has("id")) {
            return RAGFlow.toCamelCaseMap(data);
        }
        throw BizException.of(4040, "agent not found: " + id);
    }

    /**
     * 创建 agent。
     */
    public Map<String, Object> create(CreateAgentRequest req) {
        if (req == null || req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw BizException.of(4002, "title is required");
        }
        if (req.getCanvasConfig() == null || req.getCanvasConfig().isEmpty()) {
            throw BizException.of(4002, "canvasConfig (dsl) is required");
        }
        JsonNode resp = agentApi.createAgent(toBody(req));
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    /**
     * 更新 agent（partial update，仅传入字段会被更新）。
     */
    public Map<String, Object> update(String id, UpdateAgentRequest req) {
        if (id == null || id.isEmpty()) {
            throw BizException.of(4002, "id is required");
        }
        if (req == null) {
            throw BizException.of(4002, "body is required");
        }
        JsonNode resp = agentApi.updateAgent(id, toBody(req));
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    /**
     * 删除 agent。
     */
    public Map<String, Object> delete(String id) {
        if (id == null || id.isEmpty()) {
            throw BizException.of(4002, "id is required");
        }
        JsonNode resp = agentApi.deleteAgent(id);
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    // ========================= helpers =========================

    /** 将 CreateAgentRequest 转成 RAGFlow body（canvasConfig -> dsl）。 */
    private static Map<String, Object> toBody(CreateAgentRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", req.getTitle());
        body.put("dsl", req.getCanvasConfig());
        if (req.getDescription() != null) {
            body.put("description", req.getDescription());
        }
        if (req.getAvatar() != null && !req.getAvatar().isEmpty()) {
            body.put("avatar", req.getAvatar());
        }
        return body;
    }

    /** 将 UpdateAgentRequest 转成 RAGFlow body（仅非空字段）。 */
    private static Map<String, Object> toBody(UpdateAgentRequest req) {
        Map<String, Object> body = new HashMap<>();
        if (req.getTitle() != null && !req.getTitle().isEmpty()) {
            body.put("title", req.getTitle());
        }
        if (req.getCanvasConfig() != null && !req.getCanvasConfig().isEmpty()) {
            body.put("dsl", req.getCanvasConfig());
        }
        if (req.getDescription() != null) {
            body.put("description", req.getDescription());
        }
        if (req.getAvatar() != null && !req.getAvatar().isEmpty()) {
            body.put("avatar", req.getAvatar());
        }
        return body;
    }

}
