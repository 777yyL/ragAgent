package com.company.knowledge.agent;

import com.company.knowledge.agent.dto.*;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.common.util.RAGFlow;
import com.company.knowledge.integration.ragflow.ChatApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Chat 业务层。封装 {@link ChatApi}，做参数校验、字段映射、
 * 以及把 RAGFlow 对话响应的 {@code reference} 解析为 {@link ConverseResponse}（含溯源 chunks）。
 *
 * <p>Phase 6.3 的溯源关键：{@link #converse} 返回的 {@link ConverseResponse#getReference()}
 * 内含每个检索 chunk 的 content/documentId/documentName/similarity/positions。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatApi chatApi;
    private final ObjectMapper objectMapper;

    // ========================= Assistant =========================

    /**
     * 列出 chat assistant。
     */
    public List<Map<String, Object>> listAssistants() {
        JsonNode resp = chatApi.listChatAssistants();
        JsonNode data = RAGFlow.extractData(resp);
        if (data == null) {
            return Collections.emptyList();
        }
        if (data.isArray()) {
            return RAGFlow.toCamelCaseList(data);
        }
        // 兼容对象含 chats/items/list 数组
        if (data.isObject()) {
            JsonNode items = data.path("chats");
            if (!items.isArray()) items = data.path("items");
            if (!items.isArray()) items = data.path("list");
            if (items.isArray()) {
                return RAGFlow.toCamelCaseList(items);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 创建 chat assistant。
     */
    public Map<String, Object> createAssistant(CreateChatAssistantRequest req) {
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
            throw BizException.of(4002, "name is required");
        }
        JsonNode resp = chatApi.createChatAssistant(toBody(req));
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    /**
     * 删除 chat assistant。
     */
    public Map<String, Object> deleteAssistant(String id) {
        if (id == null || id.isEmpty()) {
            throw BizException.of(4002, "id is required");
        }
        JsonNode resp = chatApi.deleteChatAssistant(id);
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    // ========================= Session =========================

    /**
     * 创建会话。
     *
     * @param chatId 关联的 chat assistant ID
     * @param name   会话名称，可为 null（RAGFlow 会用默认值）
     */
    public Map<String, Object> createSession(String chatId, String name) {
        if (chatId == null || chatId.isEmpty()) {
            throw BizException.of(4002, "chatId is required");
        }
        Map<String, Object> body = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            body.put("name", name);
        }
        JsonNode resp = chatApi.createSession(chatId, body);
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    /**
     * 列出会话。
     */
    public List<Map<String, Object>> listSessions(String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            throw BizException.of(4002, "chatId is required");
        }
        JsonNode resp = chatApi.listSessions(chatId);
        JsonNode data = RAGFlow.extractData(resp);
        if (data == null) {
            return Collections.emptyList();
        }
        if (data.isArray()) {
            return RAGFlow.toCamelCaseList(data);
        }
        if (data.isObject()) {
            JsonNode items = data.path("items");
            if (items.isArray()) {
                return RAGFlow.toCamelCaseList(items);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 删除会话。
     */
    public Map<String, Object> deleteSession(String chatId, String sessionId) {
        if (chatId == null || chatId.isEmpty()) {
            throw BizException.of(4002, "chatId is required");
        }
        if (sessionId == null || sessionId.isEmpty()) {
            throw BizException.of(4002, "sessionId is required");
        }
        JsonNode resp = chatApi.deleteSession(chatId, sessionId);
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    // ========================= Converse (含溯源) =========================

    /**
     * 与 chat assistant 对话，返回 {@link ConverseResponse}（含 reference 溯源 chunks）。
     *
     * <p>核心流程：
     * <ol>
     *   <li>校验 chatId/question</li>
     *   <li>调 {@link ChatApi#converse}（非流式）</li>
     *   <li>解析 RAGFlow 的 {@code data} 对象（answer/reference/session_id/id）→ {@link ConverseResponse}</li>
     *   <li>解析 {@code reference.chunks[]} → {@link java.util.List}<{@link ReferenceChunk}></li>
     * </ol>
     */
    public ConverseResponse converse(String chatId, ConverseRequest req) {
        if (chatId == null || chatId.isEmpty()) {
            throw BizException.of(4002, "chatId is required");
        }
        if (req == null || req.getQuestion() == null || req.getQuestion().trim().isEmpty()) {
            throw BizException.of(4002, "question is required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("question", req.getQuestion());
        if (req.getDatasetIds() != null && !req.getDatasetIds().isEmpty()) {
            body.put("dataset_ids", req.getDatasetIds());
        }
        // stream 默认 false，由 ChatApi 注入

        JsonNode raw = chatApi.converse(chatId, req.getSessionId(), body);
        return parseConverseResponse(raw);
    }

    /**
     * 点赞/踩消息反馈。
     *
     * @param feedback 含 thumbup(boolean)、feedback(string, 可选)
     */
    public Map<String, Object> updateFeedback(String chatId, String sessionId, String messageId, Map<String, Object> feedback) {
        if (chatId == null || sessionId == null || messageId == null
                || chatId.isEmpty() || sessionId.isEmpty() || messageId.isEmpty()) {
            throw BizException.of(4002, "chatId/sessionId/messageId are required");
        }
        if (feedback == null) {
            feedback = new HashMap<>();
        }
        JsonNode resp = chatApi.updateMessageFeedback(chatId, sessionId, messageId, feedback);
        return RAGFlow.toCamelCaseMap(RAGFlow.extractData(resp));
    }

    // ========================= helpers =========================

    /** CreateChatAssistantRequest → RAGFlow body。 */
    private static Map<String, Object> toBody(CreateChatAssistantRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", req.getName());
        if (req.getDatasetIds() != null) {
            body.put("dataset_ids", req.getDatasetIds());
        }
        if (req.getIcon() != null && !req.getIcon().isEmpty()) {
            body.put("icon", req.getIcon());
        }
        if (req.getLlm() != null) {
            if (req.getLlm().getModel() != null && !req.getLlm().getModel().isEmpty()) {
                body.put("llm_id", req.getLlm().getModel());
            }
            Map<String, Object> llmSetting = new HashMap<>();
            if (req.getLlm().getTemperature() != null) {
                llmSetting.put("temperature", req.getLlm().getTemperature());
            }
            if (req.getLlm().getMaxTokens() != null) {
                llmSetting.put("max_tokens", req.getLlm().getMaxTokens());
            }
            if (!llmSetting.isEmpty()) {
                body.put("llm_setting", llmSetting);
            }
        }
        if (req.getPrompt() != null && !req.getPrompt().isEmpty()) {
            body.put("prompt_config", req.getPrompt());
        }
        if (req.getSimilarityThreshold() != null) {
            body.put("similarity_threshold", req.getSimilarityThreshold());
        }
        if (req.getTopK() != null) {
            body.put("top_k", req.getTopK());
        }
        return body;
    }

    /**
     * 解析 RAGFlow 对话响应。
     *
     * <p>RAGFlow 非流式响应结构：
     * <pre>{@code
     * {
     *   "code": 0,
     *   "data": {
     *     "answer": "...",
     *     "reference": {
     *       "total": 1,
     *       "chunks": [{...}, ...]
     *     },
     *     "id": "msg-id",
     *     "session_id": "session-id"
     *   }
     * }
     * }</pre>
     *
     * <p>兼容：若 RAGFlow 直接把 answer/reference 放在顶层（非 data 嵌套），也能解析。
     */
    ConverseResponse parseConverseResponse(JsonNode raw) {
        if (raw == null) {
            throw BizException.of(5003, "empty converse response");
        }
        // RAGFlow code != 0 已由 RAGFlowClient 抛 BizException，此处 data 应该存在
        JsonNode data = raw.path("data");
        // 兼容直接在顶层
        JsonNode answerNode = data.has("answer") ? data.path("answer") : raw.path("answer");
        JsonNode referenceNode = data.has("reference") ? data.path("reference") : raw.path("reference");
        JsonNode idNode = data.has("id") ? data.path("id") : raw.path("id");
        JsonNode sessionIdNode = data.has("session_id") ? data.path("session_id") : raw.path("session_id");

        ConverseResponse resp = new ConverseResponse();
        resp.setAnswer(answerNode.isTextual() ? answerNode.asText() : answerNode.toString());
        if (idNode.isTextual()) {
            resp.setId(idNode.asText());
        }
        if (sessionIdNode.isTextual()) {
            resp.setSessionId(sessionIdNode.asText());
        }
        resp.setReference(parseReference(referenceNode));
        return resp;
    }

    private Reference parseReference(JsonNode refNode) {
        Reference ref = new Reference();
        if (refNode == null || refNode.isMissingNode() || refNode.isNull() || !refNode.isObject()) {
            ref.setTotal(0);
            ref.setChunks(Collections.emptyList());
            return ref;
        }
        JsonNode totalNode = refNode.path("total");
        ref.setTotal(totalNode.isNumber() ? totalNode.asInt() : null);

        JsonNode chunksNode = refNode.path("chunks");
        List<ReferenceChunk> chunks = new ArrayList<>();
        if (chunksNode.isArray()) {
            for (JsonNode c : chunksNode) {
                chunks.add(parseChunk(c));
            }
        }
        ref.setChunks(chunks);
        return ref;
    }

    private ReferenceChunk parseChunk(JsonNode c) {
        ReferenceChunk chunk = new ReferenceChunk();
        chunk.setChunkId(textOrNull(c, "id"));
        chunk.setContent(textOrNull(c, "content"));
        chunk.setDocumentId(textOrNull(c, "document_id"));
        chunk.setDocumentName(textOrNull(c, "document_name"));
        chunk.setDatasetId(textOrNull(c, "dataset_id"));
        JsonNode sim = c.path("similarity");
        if (sim.isNumber()) {
            chunk.setSimilarity(sim.asDouble());
        }
        JsonNode pos = c.path("positions");
        if (pos.isArray()) {
            List<String> positions = new ArrayList<>();
            for (JsonNode p : pos) {
                positions.add(p.isTextual() ? p.asText() : p.toString());
            }
            chunk.setPositions(positions);
        }
        return chunk;
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        return n.isTextual() ? n.asText() : (n.isMissingNode() || n.isNull() ? null : n.toString());
    }
}
