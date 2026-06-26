package com.company.knowledge.audit.controller;

import com.company.knowledge.audit.controller.AuditWorkbenchController.ApproveRequest;
import com.company.knowledge.audit.controller.AuditWorkbenchController.SubmitRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuditWorkbenchController} 单元测试。所有依赖 mock。
 */
class AuditWorkbenchControllerTest {

    private AuditInstanceMapper instanceMapper;
    private AuditNodeRecordMapper recordMapper;
    private AuditTemplateService templateService;
    private AuditStateMachine stateMachine;
    private AuditWorkbenchController controller;

    @BeforeEach
    void setUp() {
        instanceMapper = Mockito.mock(AuditInstanceMapper.class);
        recordMapper = Mockito.mock(AuditNodeRecordMapper.class);
        templateService = Mockito.mock(AuditTemplateService.class);
        stateMachine = Mockito.mock(AuditStateMachine.class);
        controller = new AuditWorkbenchController(instanceMapper, recordMapper,
                templateService, stateMachine);
        // 设置 UserContext
        UserContext.set(new UserContext.CurrentUser(
                "u1", "Alice", "org1", "@root@G@",
                new HashSet<>(Collections.singletonList("AUDITOR_GROUP"))));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void pending_defaultPagination_returnsPage() {
        AuditInstance a = new AuditInstance();
        a.setId(1L);
        a.setStatus("PENDING");
        when(instanceMapper.selectList(any())).thenReturn(Arrays.asList(a));

        Result<PageResult<AuditInstance>> r = controller.pending("PENDING", null, 1, 20);

        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().getTotal());
        assertEquals(1, r.getData().getList().size());
    }

    @Test
    void pending_clampsInvalidPage() {
        when(instanceMapper.selectList(any())).thenReturn(Collections.emptyList());
        Result<PageResult<AuditInstance>> r = controller.pending(null, null, 0, 9999);
        assertEquals(0, r.getCode());
        assertEquals(0, r.getData().getTotal());
    }

    @Test
    void detail_normal_returnsInstanceTemplateRecords() {
        AuditInstance inst = new AuditInstance();
        inst.setId(1L);
        inst.setTemplateId(2L);
        when(instanceMapper.selectById(1L)).thenReturn(inst);

        AuditTemplate tpl = new AuditTemplate();
        tpl.setId(2L);
        when(templateService.get(2L)).thenReturn(tpl);

        AuditNodeRecord rec = new AuditNodeRecord();
        rec.setId(10L);
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(rec));

        Result<?> r = controller.detail(1L);
        assertEquals(0, r.getCode());
    }

    @Test
    void detail_notFound_returns4042() {
        when(instanceMapper.selectById(99L)).thenReturn(null);
        Result<?> r = controller.detail(99L);
        assertEquals(4042, r.getCode());
    }

    @Test
    void submit_delegatesToStateMachine() {
        AuditInstance created = new AuditInstance();
        created.setId(1L);
        created.setStatus("PENDING");
        when(stateMachine.submit(eq(50L), eq(1L), eq("u1"))).thenReturn(created);

        SubmitRequest req = new SubmitRequest();
        req.setDocId(50L);
        req.setTemplateId(1L);

        Result<AuditInstance> r = controller.submit(req);

        assertEquals(0, r.getCode());
        assertEquals(1L, r.getData().getId());
        verify(stateMachine).submit(50L, 1L, "u1");
    }

    @Test
    void approve_passesCurrentUserRole() {
        AuditInstance inst = new AuditInstance();
        inst.setId(1L);
        when(stateMachine.approve(eq(1L), eq("u1"), eq("AUDITOR_GROUP"), eq("ok")))
                .thenReturn(inst);

        ApproveRequest req = new ApproveRequest();
        req.setComment("ok");

        Result<AuditInstance> r = controller.approve(1L, req);

        assertEquals(0, r.getCode());
        verify(stateMachine).approve(1L, "u1", "AUDITOR_GROUP", "ok");
    }

    @Test
    void approve_adminRoleBypassesAuditorRoles() {
        // 切换为 ADMIN
        UserContext.set(new UserContext.CurrentUser(
                "admin", "Admin", "o", "p",
                new HashSet<>(Collections.singletonList("ADMIN"))));
        when(stateMachine.approve(eq(1L), eq("admin"), eq("ADMIN"), any()))
                .thenReturn(new AuditInstance());

        ApproveRequest req = new ApproveRequest();
        controller.approve(1L, req);

        verify(stateMachine).approve(eq(1L), eq("admin"), eq("ADMIN"), any());
    }

    @Test
    void reject_delegatesToStateMachine() {
        when(stateMachine.reject(eq(1L), anyString(), anyString(), any()))
                .thenReturn(new AuditInstance());

        ApproveRequest req = new ApproveRequest();
        req.setComment("no pass");
        controller.reject(1L, req);

        verify(stateMachine).reject(eq(1L), eq("u1"), eq("AUDITOR_GROUP"), eq("no pass"));
    }

    @Test
    void withdraw_delegatesToStateMachine() {
        when(stateMachine.withdraw(eq(1L), eq("u1"))).thenReturn(new AuditInstance());

        controller.withdraw(1L);

        verify(stateMachine).withdraw(1L, "u1");
    }

    @Test
    void mySubmitted_filtersByCurrentUser() {
        AuditInstance inst = new AuditInstance();
        inst.setId(1L);
        inst.setSubmittedBy("u1");
        when(instanceMapper.selectList(any())).thenReturn(Collections.singletonList(inst));

        Result<PageResult<AuditInstance>> r = controller.mySubmitted(null, 1, 20);

        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().getTotal());
    }

    @Test
    void myPending_returnsPendingInstances() {
        AuditInstance inst = new AuditInstance();
        inst.setStatus("PENDING");
        when(instanceMapper.selectList(any())).thenReturn(Collections.singletonList(inst));

        Result<PageResult<AuditInstance>> r = controller.myPending(1, 20);

        assertEquals(0, r.getCode());
        assertEquals(1, r.getData().getTotal());
    }
}
