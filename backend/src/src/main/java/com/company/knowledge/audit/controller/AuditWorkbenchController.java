package com.company.knowledge.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.knowledge.audit.entity.AuditInstance;
import com.company.knowledge.audit.entity.AuditNodeRecord;
import com.company.knowledge.audit.entity.AuditTemplate;
import com.company.knowledge.audit.mapper.AuditInstanceMapper;
import com.company.knowledge.audit.mapper.AuditNodeRecordMapper;
import com.company.knowledge.audit.service.AuditStateMachine;
import com.company.knowledge.audit.service.AuditTemplateService;
import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.common.result.PageResult;
import com.company.knowledge.common.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * 审核工作台 REST API。
 *
 * <p>路径：{@code /api/audit}
 *
 * <ul>
 *   <li>{@code GET  /api/audit/pending}          待审列表</li>
 *   <li>{@code GET  /api/audit/{instanceId}}     审核详情（instance + template.nodes + nodeRecords）</li>
 *   <li>{@code POST /api/audit/{id}/approve}     通过</li>
 *   <li>{@code POST /api/audit/{id}/reject}      退回</li>
 *   <li>{@code POST /api/audit/{id}/withdraw}    撤回</li>
 *   <li>{@code POST /api/audit/submit}           提交</li>
 *   <li>{@code GET  /api/audit/my-submitted}     我提交的</li>
 *   <li>{@code GET  /api/audit/my-pending}       待我审核的</li>
 * </ul>
 *
 * <p>说明：分页采用 MyBatis-Plus {@link Page}；当前项目 pom 未配置 PaginationInnerInterceptor，
 * 实际查询若未启用分页插件则 Page 会变成内存全量分页（与 {@code OperationLogController} 同策略）。
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditWorkbenchController {

    private final AuditInstanceMapper instanceMapper;
    private final AuditNodeRecordMapper recordMapper;
    private final AuditTemplateService templateService;
    private final AuditStateMachine stateMachine;
    private final com.company.knowledge.document.DocumentService documentService;
    private final com.company.knowledge.document.mapper.KnowledgeDocMapper knowledgeDocMapper;
    private final com.company.knowledge.chunk.ChunkService chunkService;

    /**
     * 待审列表（管理端）。
     *
     * @param status       可选，按状态筛选（如 PENDING/REJECTED/WITHDRAWN/APPROVED/PUBLISHED）
     * @param businessType 可选，按业务分类筛选（此字段在 knowledge_doc 表；此处只按 instance 字段过滤，
     *                     businessType 过滤需 JOIN doc，暂用入参透传由前端按结果筛选或后续接入）
     * @param page         页码，默认 1
     * @param pageSize     每页，默认 20
     */
    @GetMapping("/pending")
    public Result<PageResult<Map<String, Object>>> pending(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String businessType,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<AuditInstance> instances = queryInstances(status, businessType, null, page, pageSize);
        // 关联 knowledge_doc 获取文档标题等信息
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (AuditInstance inst : instances.getList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("instanceId", inst.getId());
            item.put("docId", inst.getDocId());
            item.put("templateId", inst.getTemplateId());
            item.put("currentNode", inst.getCurrentNode());
            item.put("status", inst.getStatus());
            item.put("submittedBy", inst.getSubmittedBy());
            item.put("submittedAt", inst.getSubmittedAt());
            // 查文档标题
            try {
                com.company.knowledge.document.entity.KnowledgeDoc doc = knowledgeDocMapper.selectById(inst.getDocId());
                if (doc != null) {
                    item.put("docTitle", doc.getTitle());
                    item.put("datasetId", doc.getDatasetId());
                    item.put("ragflowDocId", doc.getRagflowDocId());
                }
            } catch (Exception e) { /* 静默 */ }
            enriched.add(item);
        }
        return Result.success(PageResult.of(instances.getTotal(), page, pageSize, enriched));
    }

    /**
     * 审核详情：instance + template.nodes + 该实例的 node records。
     */
    @GetMapping("/{instanceId}")
    public Result<Map<String, Object>> detail(@PathVariable Long instanceId) {
        AuditInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            return Result.error(4042, "audit instance not found: " + instanceId);
        }
        AuditTemplate template = templateService.get(inst.getTemplateId());

        LambdaQueryWrapper<AuditNodeRecord> rw = new LambdaQueryWrapper<>();
        rw.eq(AuditNodeRecord::getInstanceId, instanceId)
                .orderByAsc(AuditNodeRecord::getNodeOrder)
                .orderByAsc(AuditNodeRecord::getActedAt);
        List<AuditNodeRecord> records = recordMapper.selectList(rw);

        // 查文档信息 + 切片内容
        Map<String, Object> docInfo = new HashMap<>();
        Map<String, Object> chunksData = new HashMap<>();
        try {
            com.company.knowledge.document.entity.KnowledgeDoc doc = knowledgeDocMapper.selectById(inst.getDocId());
            if (doc != null) {
                docInfo.put("title", doc.getTitle());
                docInfo.put("datasetId", doc.getDatasetId());
                docInfo.put("ragflowDocId", doc.getRagflowDocId());
                docInfo.put("businessType", doc.getBusinessType());
                docInfo.put("auditStatus", doc.getAuditStatus());
                // 查切片
                chunksData = chunkService.list(doc.getDatasetId(), doc.getRagflowDocId(), null);
            }
        } catch (Exception e) {
            docInfo.put("error", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("instanceId", inst.getId());
        out.put("docId", inst.getDocId());
        out.put("templateId", inst.getTemplateId());
        out.put("currentNode", inst.getCurrentNode());
        out.put("status", inst.getStatus());
        out.put("submittedBy", inst.getSubmittedBy());
        out.put("submittedAt", inst.getSubmittedAt());
        out.put("totalNodes", template != null && template.getNodes() != null ? template.getNodes().size() : 0);
        out.put("template", template);
        out.put("nodeRecords", records);
        out.put("docInfo", docInfo);
        out.put("chunks", chunksData != null ? chunksData.get("chunks") : java.util.Collections.emptyList());
        return Result.success(out);
    }

    /**
     * 提交审核。
     */
    @PostMapping("/submit")
    public Result<AuditInstance> submit(@RequestBody SubmitRequest req) {
        String submitterId = UserContext.get() == null ? null : UserContext.get().getPersonId();
        // 用 ragflowDocId 查 knowledge_doc 获取本地 id
        Long docId = documentService.getOrCreateDocId(req.getDatasetId(), req.getRagflowDocId(), submitterId);
        AuditInstance inst = stateMachine.submit(docId, req.getTemplateId(), submitterId);
        return Result.success(inst);
    }

    /**
     * 通过当前节点。
     */
    @PostMapping("/{instanceId}/approve")
    public Result<AuditInstance> approve(@PathVariable Long instanceId,
                                         @RequestBody ApproveRequest req) {
        UserContext.CurrentUser u = UserContext.require();
        String role = pickApproverRole(u);
        AuditInstance inst = stateMachine.approve(instanceId, u.getPersonId(), role, req.getComment());
        return Result.success(inst);
    }

    /**
     * 退回当前节点。
     */
    @PostMapping("/{instanceId}/reject")
    public Result<AuditInstance> reject(@PathVariable Long instanceId,
                                        @RequestBody ApproveRequest req) {
        UserContext.CurrentUser u = UserContext.require();
        String role = pickApproverRole(u);
        AuditInstance inst = stateMachine.reject(instanceId, u.getPersonId(), role, req.getComment());
        return Result.success(inst);
    }

    /**
     * 撤回（提交人本人）。
     */
    @PostMapping("/{instanceId}/withdraw")
    public Result<AuditInstance> withdraw(@PathVariable Long instanceId) {
        UserContext.CurrentUser u = UserContext.require();
        AuditInstance inst = stateMachine.withdraw(instanceId, u.getPersonId());
        return Result.success(inst);
    }

    /**
     * 我提交的。
     */
    @GetMapping("/my-submitted")
    public Result<PageResult<AuditInstance>> mySubmitted(@RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        UserContext.CurrentUser u = UserContext.require();
        return Result.success(queryInstances(status, null, u.getPersonId(), page, pageSize));
    }

    /**
     * 待我审核的（基于 UserContext 角色匹配 template.nodes[].approverRole）。
     *
     * <p>简化实现：返回所有 PENDING 实例（前端按角色二次过滤）。
     * 精确实现需要 JOIN template 解析 JSONB nodes，本处先按 instance.status=PENDING 返回，
     * 前端可对 template.nodes[currentNode-1].approverRole 进行匹配。
     */
    @GetMapping("/my-pending")
    public Result<PageResult<AuditInstance>> myPending(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        // 若用户是 ADMIN，返回所有 PENDING；否则也返回所有 PENDING（前端按 template 过滤）
        return Result.success(queryInstances("PENDING", null, null, page, pageSize));
    }

    // ===== 辅助 =====

    private PageResult<AuditInstance> queryInstances(String status, String businessType,
                                                     String submitter, int page, int pageSize) {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 200) {
            pageSize = 20;
        }
        LambdaQueryWrapper<AuditInstance> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            w.eq(AuditInstance::getStatus, status);
        }
        if (StringUtils.hasText(submitter)) {
            w.eq(AuditInstance::getSubmittedBy, submitter);
        }
        if (StringUtils.hasText(businessType)) {
            // 当前 instance 表无 businessType 字段，此处忽略；待 JOIN doc 实现
        }
        w.orderByDesc(AuditInstance::getSubmittedAt);

        // 未启用分页插件：用 selectList + 内存分页（与 OperationLogController 同策略）
        List<AuditInstance> all = instanceMapper.selectList(w);
        int total = all.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return PageResult.of(total, page, pageSize, all.subList(from, to));
    }

    /**
     * 从 UserContext 角色集合中取一个「审批角色」。
     *
     * <p>优先级：AUDITOR_GROUP > AUDITOR_REGION > AUDITOR_ENTERPRISE > ADMIN > 其他。
     * 这只是用于填入 node_record.approver_role；状态机内部还会再做模板节点角色匹配。
     */
    private String pickApproverRole(UserContext.CurrentUser u) {
        if (u.hasRole("AUDITOR_GROUP")) {
            return "AUDITOR_GROUP";
        }
        if (u.hasRole("AUDITOR_REGION")) {
            return "AUDITOR_REGION";
        }
        if (u.hasRole("AUDITOR_ENTERPRISE")) {
            return "AUDITOR_ENTERPRISE";
        }
        if (u.hasRole("ADMIN")) {
            return "ADMIN";
        }
        // 兜底：取第一个角色
        if (u.getRoles() != null && !u.getRoles().isEmpty()) {
            return u.getRoles().iterator().next();
        }
        return "UNKNOWN";
    }

    // ===== 请求 DTO =====

    @Data
    public static class SubmitRequest {
        private String datasetId;
        private String ragflowDocId;
        private Long templateId;
    }

    @Data
    public static class ApproveRequest {
        private String comment;
    }
}
