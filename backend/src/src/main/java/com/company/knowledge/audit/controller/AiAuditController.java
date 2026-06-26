package com.company.knowledge.audit.controller;

import com.company.knowledge.audit.entity.AiAuditIssue;
import com.company.knowledge.audit.service.AiAuditService;
import com.company.knowledge.common.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 审核 REST API。
 *
 * <p>路径：{@code /api/audit/ai}
 *
 * <ul>
 *   <li>{@code POST /api/audit/ai/run}           触发 AI 审核（异步）</li>
 *   <li>{@code GET  /api/audit/ai/issues}        问题列表</li>
 *   <li>{@code POST /api/audit/ai/issues/batch}  批量采纳/忽略</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/audit/ai")
@RequiredArgsConstructor
public class AiAuditController {

    private final AiAuditService service;

    /**
     * 触发 AI 审核（异步）。返回成功即任务已下发，调用方轮询 issues 接口看结果。
     */
    @PostMapping("/run")
    public Result<Void> run(@RequestBody RunRequest req) {
        service.runAiAudit(req.getDatasetId(), req.getDocId());
        return Result.success();
    }

    /**
     * 查询某文档的 AI 审核问题。
     */
    @GetMapping("/issues")
    public Result<List<AiAuditIssue>> issues(@RequestParam Long docId,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) String severity,
                                             @RequestParam(required = false) String status) {
        return Result.success(service.reportByDoc(docId, type, severity, status));
    }

    /**
     * 批量处理问题。
     */
    @PostMapping("/issues/batch")
    public Result<Integer> batch(@RequestBody BatchRequest req) {
        int n = service.batchResolve(req.getIds(), req.getAction());
        return Result.success(n);
    }

    // ===== 请求 DTO =====

    @Data
    public static class RunRequest {
        private String datasetId;
        private Long docId;
    }

    @Data
    public static class BatchRequest {
        private List<Long> ids;
        /** RESOLVE / IGNORE */
        private String action;
    }
}
