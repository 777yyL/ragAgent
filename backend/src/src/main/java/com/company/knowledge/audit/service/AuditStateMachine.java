package com.company.knowledge.audit.service;

import com.company.knowledge.audit.constant.AuditAction;
import com.company.knowledge.audit.constant.AuditStatus;
import com.company.knowledge.audit.entity.AuditInstance;
import com.company.knowledge.audit.entity.AuditNode;
import com.company.knowledge.audit.entity.AuditNodeRecord;
import com.company.knowledge.audit.entity.AuditTemplate;
import com.company.knowledge.audit.mapper.AuditInstanceMapper;
import com.company.knowledge.audit.mapper.AuditNodeRecordMapper;
import com.company.knowledge.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审核状态机核心。封装实例的 submit/approve/reject/withdraw 状态转换。
 *
 * <p>所有状态校验失败统一抛 {@link BizException}(code=4031)。
 *
 * <p>终审通过（approve 到最后一个节点）会调用 {@link PublishService#publish(Long)}
 * 把 chunks 切到 available=true。
 *
 * <p>事务：每个动作整体 @Transactional，失败回滚；注意 {@link PublishService}
 * 内的 RAGFlow 调用是外部 HTTP，不在事务内（HTTP 失败需人工补偿，但 instance
 * 状态会先回滚）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditStateMachine {

    /** 非法状态转换错误码 */
    public static final int CODE_ILLEGAL_TRANSITION = 4031;
    /** 角色不匹配错误码 */
    public static final int CODE_ROLE_MISMATCH = 4032;
    /** 实例不存在错误码 */
    public static final int CODE_INSTANCE_NOT_FOUND = 4042;
    /** 模板节点错误码 */
    public static final int CODE_BAD_TEMPLATE = 4003;

    private final AuditInstanceMapper instanceMapper;
    private final AuditNodeRecordMapper recordMapper;
    private final AuditTemplateService templateService;
    private final com.company.knowledge.document.mapper.KnowledgeDocMapper knowledgeDocMapper;
    private final PublishService publishService;
    private final com.company.knowledge.chunk.ChunkService chunkService;

    /**
     * 提交审核。DRAFT/REJECTED/WITHDRAWN → PENDING，currentNode=1。
     *
     * <p>同一个 docId 可多次提交，每次提交创建新实例（旧实例作为历史保留）。
     *
     * @param docId       文档 ID
     * @param templateId  模板 ID
     * @param submitterId 提交人 personId
     * @return 新建的实例
     */
    @Transactional
    public AuditInstance submit(Long docId, Long templateId, String submitterId) {
        AuditTemplate template = templateService.get(templateId);
        validateTemplateNodes(template);

        // 校验文档审核状态：只允许 DRAFT/REJECTED/WITHDRAWN 提交
        com.company.knowledge.document.entity.KnowledgeDoc kdoc = knowledgeDocMapper.selectById(docId);
        if (kdoc != null) {
            String currentStatus = kdoc.getAuditStatus();
            if (currentStatus != null && !currentStatus.equals("DRAFT")
                    && !currentStatus.equals("REJECTED") && !currentStatus.equals("WITHDRAWN")) {
                throw BizException.of(4045, "文档当前审核状态为 " + currentStatus + "，不允许重复提交");
            }
            // 更新为 PENDING
            kdoc.setAuditStatus("PENDING");
            kdoc.setUpdatedAt(LocalDateTime.now());
            knowledgeDocMapper.updateById(kdoc);

            // 关键：提交审核时锁定切片（available=false），防止未审核内容被检索/对话
            lockChunks(kdoc);
        }

        AuditInstance inst = new AuditInstance();
        inst.setDocId(docId);
        inst.setTemplateId(templateId);
        inst.setCurrentNode(1);
        inst.setStatus(AuditStatus.PENDING.name());
        inst.setSubmittedBy(submitterId);
        inst.setSubmittedAt(LocalDateTime.now());
        instanceMapper.insert(inst);

        // 写一条 SUBMIT 记录
        writeRecord(inst.getId(), 1, submitterId, "EDITOR", AuditAction.SUBMIT, "submitted");
        log.info("audit instance created: id={}, docId={}, template={}", inst.getId(), docId, templateId);
        return inst;
    }

    /**
     * 审批通过当前节点。
     *
     * <p>校验 approverRole 是否匹配当前节点；若当前节点 == 最后节点 → APPROVED
     * 并触发 {@link PublishService#publish}；否则 currentNode++ 仍 PENDING。
     *
     * @param instanceId 实例 ID
     * @param approverId 审批人 personId
     * @param approverRole 审批人当前角色（来自 UserContext.hasRole）
     * @param comment    审批意见，可空
     */
    @Transactional
    public AuditInstance approve(Long instanceId, String approverId, String approverRole, String comment) {
        AuditInstance inst = requireInstance(instanceId);
        if (!AuditStatus.PENDING.name().equals(inst.getStatus())) {
            throw BizException.of(CODE_ILLEGAL_TRANSITION,
                    "approve requires PENDING, current=" + inst.getStatus());
        }

        AuditTemplate template = templateService.get(inst.getTemplateId());
        AuditNode currentNode = findNodeByOrder(template, inst.getCurrentNode());
        if (currentNode == null) {
            throw BizException.of(CODE_BAD_TEMPLATE,
                    "template " + template.getId() + " missing node order=" + inst.getCurrentNode());
        }
        // 角色校验：ADMIN 可越权，否则必须匹配
        if (!"ADMIN".equals(approverRole) && !currentNode.getApproverRole().equals(approverRole)) {
            throw BizException.of(CODE_ROLE_MISMATCH,
                    "current node requires role=" + currentNode.getApproverRole()
                            + ", got=" + approverRole);
        }

        // 写通过记录
        writeRecord(inst.getId(), inst.getCurrentNode(), approverId,
                approverRole, AuditAction.APPROVE, comment);

        int lastOrder = lastOrder(template);
        if (inst.getCurrentNode() == lastOrder) {
            // 终审通过
            inst.setStatus(AuditStatus.APPROVED.name());
            inst.setFinishedAt(LocalDateTime.now());
            instanceMapper.updateById(inst);
            log.info("audit approved: instanceId={}, docId={}", instanceId, inst.getDocId());
            // 触发发布（HTTP 外部调用，可能抛异常；抛出后 instance 状态是否回滚取决于事务策略：
            // 当前是 @Transactional，HTTP 失败会回滚 APPROVED→PENDING，需调用方重试）
            publishService.publish(instanceId);
        } else {
            // 推进下一节点
            inst.setCurrentNode(inst.getCurrentNode() + 1);
            instanceMapper.updateById(inst);
            log.info("audit advanced: instanceId={}, currentNode={}/{}",
                    instanceId, inst.getCurrentNode(), lastOrder);
        }
        return inst;
    }

    /**
     * 退回当前节点。PENDING → REJECTED。编辑员修改后可重新 submit。
     *
     * <p>角色校验同 {@link #approve}。
     */
    @Transactional
    public AuditInstance reject(Long instanceId, String approverId, String approverRole, String comment) {
        AuditInstance inst = requireInstance(instanceId);
        if (!AuditStatus.PENDING.name().equals(inst.getStatus())) {
            throw BizException.of(CODE_ILLEGAL_TRANSITION,
                    "reject requires PENDING, current=" + inst.getStatus());
        }
        AuditTemplate template = templateService.get(inst.getTemplateId());
        AuditNode currentNode = findNodeByOrder(template, inst.getCurrentNode());
        if (currentNode == null) {
            throw BizException.of(CODE_BAD_TEMPLATE, "template missing node");
        }
        if (!"ADMIN".equals(approverRole) && !currentNode.getApproverRole().equals(approverRole)) {
            throw BizException.of(CODE_ROLE_MISMATCH,
                    "reject requires role=" + currentNode.getApproverRole() + ", got=" + approverRole);
        }

        writeRecord(inst.getId(), inst.getCurrentNode(), approverId,
                approverRole, AuditAction.REJECT, comment);

        inst.setStatus(AuditStatus.REJECTED.name());
        inst.setFinishedAt(LocalDateTime.now());
        instanceMapper.updateById(inst);

        // 同步更新 knowledge_doc → REJECTED（允许重新提交）
        com.company.knowledge.document.entity.KnowledgeDoc kdoc = knowledgeDocMapper.selectById(inst.getDocId());
        if (kdoc != null) {
            kdoc.setAuditStatus("REJECTED");
            kdoc.setUpdatedAt(LocalDateTime.now());
            knowledgeDocMapper.updateById(kdoc);
        }
        log.info("audit rejected: instanceId={}, by={}, comment={}", instanceId, approverId, comment);
        return inst;
    }

    /**
     * 撤回。PENDING → WITHDRAWN。
     *
     * <p>只有提交人本人或 ADMIN 可撤回。
     */
    @Transactional
    public AuditInstance withdraw(Long instanceId, String submitterId) {
        AuditInstance inst = requireInstance(instanceId);
        if (!AuditStatus.PENDING.name().equals(inst.getStatus())) {
            throw BizException.of(CODE_ILLEGAL_TRANSITION,
                    "withdraw requires PENDING, current=" + inst.getStatus());
        }
        // 只有提交人本人或 ADMIN 可撤回；ADMIN 由调用方层判断，本处只校验本人
        if (submitterId != null && !submitterId.equals(inst.getSubmittedBy())
                && !"ADMIN".equals(submitterId)) {
            throw BizException.of(CODE_ILLEGAL_TRANSITION,
                    "only submitter or ADMIN can withdraw");
        }

        writeRecord(inst.getId(), inst.getCurrentNode(), submitterId,
                "SUBMITTER", AuditAction.WITHDRAW, "withdrawn");

        inst.setStatus(AuditStatus.WITHDRAWN.name());
        inst.setFinishedAt(LocalDateTime.now());
        instanceMapper.updateById(inst);
        log.info("audit withdrawn: instanceId={}, by={}", instanceId, submitterId);
        return inst;
    }

    // ===== 辅助 =====

    private AuditInstance requireInstance(Long id) {
        AuditInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            throw BizException.of(CODE_INSTANCE_NOT_FOUND, "audit instance not found: " + id);
        }
        return inst;
    }

    private void writeRecord(Long instanceId, int nodeOrder, String approverId,
                             String approverRole, AuditAction action, String comment) {
        AuditNodeRecord r = new AuditNodeRecord();
        r.setInstanceId(instanceId);
        r.setNodeOrder(nodeOrder);
        r.setApproverId(approverId);
        r.setApproverRole(approverRole);
        r.setAction(action.name());
        r.setComment(comment);
        r.setActedAt(LocalDateTime.now());
        recordMapper.insert(r);
    }

    private AuditNode findNodeByOrder(AuditTemplate template, int order) {
        if (template.getNodes() == null) {
            return null;
        }
        for (AuditNode n : template.getNodes()) {
            if (n.getOrder() == order) {
                return n;
            }
        }
        return null;
    }

    private int lastOrder(AuditTemplate template) {
        int max = 0;
        for (AuditNode n : template.getNodes()) {
            if (n.getOrder() > max) {
                max = n.getOrder();
            }
        }
        return max;
    }

    private void validateTemplateNodes(AuditTemplate template) {
        if (template.getNodes() == null || template.getNodes().isEmpty()) {
            throw BizException.of(CODE_BAD_TEMPLATE,
                    "template " + template.getId() + " has no nodes");
        }
    }

    /**
     * 提交审核时锁定切片（available=false），防止未审核内容被检索/对话。
     */
    @SuppressWarnings("unchecked")
    private void lockChunks(com.company.knowledge.document.entity.KnowledgeDoc kdoc) {
        String datasetId = kdoc.getDatasetId();
        String ragflowDocId = kdoc.getRagflowDocId();
        if (datasetId == null || ragflowDocId == null) {
            log.warn("lockChunks skipped: missing datasetId or ragflowDocId for docId={}", kdoc.getId());
            return;
        }
        try {
            java.util.Map<String, Object> resp = chunkService.list(datasetId, ragflowDocId, null);
            Object chunksObj = resp != null ? resp.get("chunks") : null;
            if (!(chunksObj instanceof java.util.List)) {
                log.warn("lockChunks: no chunks found for datasetId={}, ragflowDocId={}", datasetId, ragflowDocId);
                return;
            }
            java.util.List<Map<String, Object>> chunks = (java.util.List<Map<String, Object>>) chunksObj;
            java.util.List<String> chunkIds = new java.util.ArrayList<>();
            for (Map<String, Object> chunk : chunks) {
                Object id = chunk.get("id");
                if (id != null) chunkIds.add(String.valueOf(id));
            }
            if (!chunkIds.isEmpty()) {
                chunkService.setAvailability(datasetId, ragflowDocId, chunkIds, false);
                log.info("lockChunks: {} chunks locked (available=false) for docId={}", chunkIds.size(), kdoc.getId());
            }
        } catch (Exception e) {
            log.warn("lockChunks failed (non-fatal): {}", e.getMessage());
        }
    }
}
