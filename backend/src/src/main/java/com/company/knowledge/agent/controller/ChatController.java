package com.company.knowledge.agent.controller;

import com.company.knowledge.agent.ChatService;
import com.company.knowledge.agent.dto.ConverseRequest;
import com.company.knowledge.agent.dto.ConverseResponse;
import com.company.knowledge.agent.dto.CreateChatAssistantRequest;
import com.company.knowledge.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chat REST API。
 *
 * <ul>
 *   <li>{@code GET    /api/chats}                                       助手列表</li>
 *   <li>{@code POST   /api/chats}                                       创建助手</li>
 *   <li>{@code DELETE /api/chats/{chatId}}                              删除助手</li>
 *   <li>{@code GET    /api/chats/{chatId}/sessions}                     会话列表</li>
 *   <li>{@code POST   /api/chats/{chatId}/sessions}                     创建会话</li>
 *   <li>{@code DELETE /api/chats/{chatId}/sessions/{sessionId}}         删除会话</li>
 *   <li>{@code POST   /api/chats/{chatId}/completions}                  对话（返回溯源）</li>
 *   <li>{@code PATCH  /api/chats/{chatId}/sessions/{sessionId}/messages/{messageId}/feedback}  反馈</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ========================= Assistant =========================

    @GetMapping
    public Result<List<Map<String, Object>>> listAssistants() {
        return Result.success(chatService.listAssistants());
    }

    @PostMapping
    public Result<Map<String, Object>> createAssistant(@RequestBody CreateChatAssistantRequest req) {
        return Result.success(chatService.createAssistant(req));
    }

    @DeleteMapping("/{chatId}")
    public Result<Map<String, Object>> deleteAssistant(@PathVariable String chatId) {
        return Result.success(chatService.deleteAssistant(chatId));
    }

    // ========================= Session =========================

    @GetMapping("/{chatId}/sessions")
    public Result<List<Map<String, Object>>> listSessions(@PathVariable String chatId) {
        return Result.success(chatService.listSessions(chatId));
    }

    @PostMapping("/{chatId}/sessions")
    public Result<Map<String, Object>> createSession(@PathVariable String chatId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        String name = null;
        if (body != null) {
            Object n = body.get("name");
            if (n != null) {
                name = String.valueOf(n);
            }
        }
        return Result.success(chatService.createSession(chatId, name));
    }

    @DeleteMapping("/{chatId}/sessions/{sessionId}")
    public Result<Map<String, Object>> deleteSession(@PathVariable String chatId,
                                          @PathVariable String sessionId) {
        return Result.success(chatService.deleteSession(chatId, sessionId));
    }

    // ========================= Completion（含溯源） =========================

    @PostMapping("/{chatId}/completions")
    public Result<ConverseResponse> converse(@PathVariable String chatId,
                                             @RequestBody ConverseRequest req) {
        return Result.success(chatService.converse(chatId, req));
    }

    // ========================= Feedback =========================

    @PatchMapping("/{chatId}/sessions/{sessionId}/messages/{messageId}/feedback")
    public Result<Map<String, Object>> updateFeedback(@PathVariable String chatId,
                                           @PathVariable String sessionId,
                                           @PathVariable String messageId,
                                           @RequestBody(required = false) Map<String, Object> feedback) {
        return Result.success(chatService.updateFeedback(chatId, sessionId, messageId, feedback));
    }
}
