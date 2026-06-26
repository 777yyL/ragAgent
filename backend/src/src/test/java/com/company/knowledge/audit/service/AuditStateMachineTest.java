package com.company.knowledge.audit.service;

import com.company.knowledge.audit.constant.AuditStatus;
import com.company.knowledge.audit.entity.AuditInstance;
import com.company.knowledge.audit.entity.AuditNode;
import com.company.knowledge.audit.entity.AuditTemplate;
import com.company.knowledge.audit.mapper.AuditInstanceMapper;
import com.company.knowledge.audit.mapper.AuditNodeRecordMapper;
import com.company.knowledge.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link AuditStateMachine} 单元测试。覆盖：
 * <ul>
 *   <li>一级直审：submit→approve→PUBLISHED</li>
 *   <li>三级：submit→approve(1)→approve(2)→approve(3)→PUBLISHED</li>
 *   <li>退回：submit→reject→REJECTED→重新 submit</li>
 *   <li>撤回：submit→withdraw→WITHDRAWN</li>
 *   <li>非法转换：APPROVED→approve 抛异常</li>
 *   <li>角色不匹配：EDITOR 尝试 approve 抛异常</li>
 *   <li>instance 不存在 / 模板节点缺失</li>
 * </ul>
 *
 * <p>所有 mapper 与 PublishService 全部 mock，不依赖真实 DB 或 RAGFlow。
 */
class AuditStateMachineTest {

    private AuditInstanceMapper instanceMapper;
    private AuditNodeRecordMapper recordMapper;
    private AuditTemplateService templateService;
    private PublishService publishService;
    private AuditStateMachine machine;

    @BeforeEach
    void setUp() {
        instanceMapper = Mockito.mock(AuditInstanceMapper.class);
        recordMapper = Mockito.mock(AuditNodeRecordMapper.class);
        templateService = Mockito.mock(AuditTemplateService.class);
        publishService = Mockito.mock(PublishService.class);
        machine = new AuditStateMachine(instanceMapper, recordMapper,
                templateService, publishService);

        // insert 时模拟主键回填
        when(instanceMapper.insert(any())).thenAnswer(inv -> {
            AuditInstance i = inv.getArgument(0);
            i.setId(100L);
            return 1;
        });
        when(recordMapper.insert(any())).thenReturn(1);
    }

    // ===== submit =====

    @Test
    void submit_normal_createsInstancePENDING() {
        AuditTemplate tpl = oneLevelTemplate();
        when(templateService.get(1L)).thenReturn(tpl);

        AuditInstance out = machine.submit(50L, 1L, "u1");

        assertEquals(AuditStatus.PENDING.name(), out.getStatus());
        assertEquals(1, out.getCurrentNode().intValue());
        assertEquals("u1", out.getSubmittedBy());

        ArgumentCaptor<AuditInstance> cap = ArgumentCaptor.forClass(AuditInstance.class);
        verify(instanceMapper).insert(cap.capture());
        assertEquals(AuditStatus.PENDING.name(), cap.getValue().getStatus());

        // 应写一条 SUBMIT 记录
        verify(recordMapper).insert(any());
    }

    @Test
    void submit_emptyTemplateNodes_throwsBizException() {
        AuditTemplate tpl = new AuditTemplate();
        tpl.setId(1L);
        tpl.setNodes(null);
        when(templateService.get(1L)).thenReturn(tpl);
        assertThrows(BizException.class, () -> machine.submit(50L, 1L, "u1"));
    }

    // ===== 一级直审 =====

    @Test
    void oneLevel_approveAtLast_triggersPublishAndPUBLISHED() {
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        when(templateService.get(1L)).thenReturn(oneLevelTemplate());
        when(instanceMapper.selectById(100L)).thenReturn(inst);
        when(instanceMapper.updateById(any())).thenReturn(1);

        machine.approve(100L, "auditor1", "AUDITOR_GROUP", "ok");

        assertEquals(AuditStatus.APPROVED.name(), inst.getStatus());
        verify(publishService).publish(100L);
        verify(recordMapper).insert(any());
    }

    // ===== 三级 =====

    @Test
    void threeLevel_approveSequentially_triggersPublishOnlyAtLast() {
        AuditTemplate tpl = threeLevelTemplate();
        when(templateService.get(1L)).thenReturn(tpl);
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        // node 1: 企业
        machine.approve(100L, "u_ae", "AUDITOR_ENTERPRISE", "pass1");
        assertEquals(AuditStatus.PENDING.name(), inst.getStatus());
        assertEquals(2, inst.getCurrentNode().intValue());
        verify(publishService, never()).publish(anyLong());

        // node 2: 区域
        machine.approve(100L, "u_ar", "AUDITOR_REGION", "pass2");
        assertEquals(AuditStatus.PENDING.name(), inst.getStatus());
        assertEquals(3, inst.getCurrentNode().intValue());
        verify(publishService, never()).publish(anyLong());

        // node 3: 集团
        machine.approve(100L, "u_ag", "AUDITOR_GROUP", "final pass");
        assertEquals(AuditStatus.APPROVED.name(), inst.getStatus());
        verify(publishService, times(1)).publish(100L);

        // 应写 3 条 APPROVE 记录
        verify(recordMapper, times(3)).insert(any());
    }

    // ===== 退回 =====

    @Test
    void reject_normal_setsREJECTED() {
        AuditTemplate tpl = oneLevelTemplate();
        when(templateService.get(1L)).thenReturn(tpl);
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        machine.reject(100L, "auditor1", "AUDITOR_GROUP", "不通过");

        assertEquals(AuditStatus.REJECTED.name(), inst.getStatus());
        verify(publishService, never()).publish(anyLong());
    }

    @Test
    void rejected_resubmit_createsNewInstance() {
        // 模拟 reject 后再次 submit：测试 submit 不会因为旧的 rejected 实例失败
        when(templateService.get(1L)).thenReturn(oneLevelTemplate());

        AuditInstance inst = machine.submit(50L, 1L, "u1");
        assertEquals(AuditStatus.PENDING.name(), inst.getStatus());
    }

    // ===== 撤回 =====

    @Test
    void withdraw_bySubmitter_setsWITHDRAWN() {
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        inst.setSubmittedBy("u1");
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        machine.withdraw(100L, "u1");

        assertEquals(AuditStatus.WITHDRAWN.name(), inst.getStatus());
    }

    @Test
    void withdraw_byOther_throwsIllegalTransition() {
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        inst.setSubmittedBy("u1");
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        assertThrows(BizException.class, () -> machine.withdraw(100L, "u_other"));
    }

    // ===== 非法转换 =====

    @Test
    void approve_onAPPROVED_throwsIllegalTransition() {
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.APPROVED);
        when(instanceMapper.selectById(100L)).thenReturn(inst);
        when(templateService.get(1L)).thenReturn(oneLevelTemplate());

        BizException ex = assertThrows(BizException.class,
                () -> machine.approve(100L, "a", "AUDITOR_GROUP", "x"));
        assertEquals(AuditStateMachine.CODE_ILLEGAL_TRANSITION, ex.getCode());
    }

    @Test
    void approve_onREJECTED_throwsIllegalTransition() {
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.REJECTED);
        when(instanceMapper.selectById(100L)).thenReturn(inst);
        when(templateService.get(1L)).thenReturn(oneLevelTemplate());

        assertThrows(BizException.class,
                () -> machine.approve(100L, "a", "AUDITOR_GROUP", "x"));
    }

    @Test
    void reject_onDRAFT_throwsIllegalTransition() {
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.DRAFT);
        when(instanceMapper.selectById(100L)).thenReturn(inst);
        when(templateService.get(1L)).thenReturn(oneLevelTemplate());

        assertThrows(BizException.class,
                () -> machine.reject(100L, "a", "AUDITOR_GROUP", "x"));
    }

    // ===== 角色校验 =====

    @Test
    void approve_wrongRole_throwsRoleMismatch() {
        AuditTemplate tpl = oneLevelTemplate(); // 要求 AUDITOR_GROUP
        when(templateService.get(1L)).thenReturn(tpl);
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        BizException ex = assertThrows(BizException.class,
                () -> machine.approve(100L, "editor", "EDITOR", "x"));
        assertEquals(AuditStateMachine.CODE_ROLE_MISMATCH, ex.getCode());
    }

    @Test
    void approve_adminBypassRole_succeeds() {
        AuditTemplate tpl = oneLevelTemplate();
        when(templateService.get(1L)).thenReturn(tpl);
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        machine.approve(100L, "admin", "ADMIN", "admin override");
        assertEquals(AuditStatus.APPROVED.name(), inst.getStatus());
    }

    @Test
    void reject_wrongRole_throwsRoleMismatch() {
        AuditTemplate tpl = oneLevelTemplate();
        when(templateService.get(1L)).thenReturn(tpl);
        AuditInstance inst = makeInstance(100L, 1, AuditStatus.PENDING);
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        assertThrows(BizException.class,
                () -> machine.reject(100L, "viewer", "VIEWER", "x"));
    }

    // ===== 实例/模板缺失 =====

    @Test
    void approve_instanceNotFound_throws() {
        when(instanceMapper.selectById(999L)).thenReturn(null);
        assertThrows(BizException.class,
                () -> machine.approve(999L, "a", "AUDITOR_GROUP", "x"));
    }

    @Test
    void approve_templateMissingCurrentNode_throws() {
        // 模板只有 node order=1，但实例 currentNode=3
        AuditTemplate tpl = oneLevelTemplate();
        when(templateService.get(1L)).thenReturn(tpl);
        AuditInstance inst = makeInstance(100L, 3, AuditStatus.PENDING);
        when(instanceMapper.selectById(100L)).thenReturn(inst);

        BizException ex = assertThrows(BizException.class,
                () -> machine.approve(100L, "a", "AUDITOR_GROUP", "x"));
        assertEquals(AuditStateMachine.CODE_BAD_TEMPLATE, ex.getCode());
    }

    // ===== 辅助构造 =====

    private AuditTemplate oneLevelTemplate() {
        AuditTemplate t = new AuditTemplate();
        t.setId(1L);
        List<AuditNode> nodes = Arrays.asList(
                new AuditNode(1, "集团终审", "AUDITOR_GROUP", false));
        t.setNodes(nodes);
        return t;
    }

    private AuditTemplate threeLevelTemplate() {
        AuditTemplate t = new AuditTemplate();
        t.setId(1L);
        List<AuditNode> nodes = Arrays.asList(
                new AuditNode(1, "企业", "AUDITOR_ENTERPRISE", false),
                new AuditNode(2, "区域", "AUDITOR_REGION", false),
                new AuditNode(3, "集团", "AUDITOR_GROUP", false));
        t.setNodes(nodes);
        return t;
    }

    private AuditInstance makeInstance(long id, int currentNode, AuditStatus status) {
        AuditInstance i = new AuditInstance();
        i.setId(id);
        i.setDocId(50L);
        i.setTemplateId(1L);
        i.setCurrentNode(currentNode);
        i.setStatus(status.name());
        i.setSubmittedBy("u1");
        return i;
    }
}
