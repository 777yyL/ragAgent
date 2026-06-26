package com.company.knowledge.agent.controller;

import com.company.knowledge.agent.AgentService;
import com.company.knowledge.agent.dto.CreateAgentRequest;
import com.company.knowledge.agent.dto.UpdateAgentRequest;
import com.company.knowledge.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent REST API。
 *
 * <ul>
 *   <li>{@code GET    /api/agents}           列出全部 agent</li>
 *   <li>{@code POST   /api/agents}           创建 agent</li>
 *   <li>{@code PUT    /api/agents/{id}}      更新 agent</li>
 *   <li>{@code DELETE /api/agents/{id}}      删除 agent</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(agentService.list());
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateAgentRequest req) {
        return Result.success(agentService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @RequestBody UpdateAgentRequest req) {
        return Result.success(agentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable String id) {
        return Result.success(agentService.delete(id));
    }
}
