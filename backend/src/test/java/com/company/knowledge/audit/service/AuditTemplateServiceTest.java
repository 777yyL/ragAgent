package com.company.knowledge.audit.service;

import com.company.knowledge.audit.entity.AuditNode;
import com.company.knowledge.audit.entity.AuditTemplate;
import com.company.knowledge.audit.mapper.AuditTemplateMapper;
import com.company.knowledge.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link AuditTemplateService} 单元测试。Mapper mock。
 */
class AuditTemplateServiceTest {

    private AuditTemplateMapper mapper;
    private AuditTemplateService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(AuditTemplateMapper.class);
        service = new AuditTemplateService(mapper);
    }

    @Test
    void create_normal_insertsAndNormalizesOrder() {
        List<AuditNode> nodes = Arrays.asList(
                new AuditNode(0, "企业", "AUDITOR_ENTERPRISE", false),
                new AuditNode(0, "集团", "AUDITOR_GROUP", false));
        when(mapper.insert(any())).thenReturn(1);

        AuditTemplate out = service.create("T1", "ops", nodes);

        ArgumentCaptor<AuditTemplate> cap = ArgumentCaptor.forClass(AuditTemplate.class);
        verify(mapper).insert(cap.capture());
        assertEquals("T1", cap.getValue().getName());
        // order 应被规范化为 1,2
        assertEquals(1, cap.getValue().getNodes().get(0).getOrder());
        assertEquals(2, cap.getValue().getNodes().get(1).getOrder());
        assertTrue(cap.getValue().getEnabled());
    }

    @Test
    void create_emptyNodes_throwsBizException() {
        assertThrows(BizException.class,
                () -> service.create("T", "ops", Collections.emptyList()));
    }

    @Test
    void create_nullApproverRole_throwsBizException() {
        List<AuditNode> nodes = Collections.singletonList(
                new AuditNode(1, "x", null, false));
        assertThrows(BizException.class,
                () -> service.create("T", "ops", nodes));
    }

    @Test
    void get_notFound_throws() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.get(99L));
    }

    @Test
    void get_found_returnsTemplate() {
        AuditTemplate t = new AuditTemplate();
        t.setId(1L);
        t.setName("T1");
        when(mapper.selectById(1L)).thenReturn(t);
        assertSame(t, service.get(1L));
    }

    @Test
    void update_partialFields_appliesOnlyProvided() {
        AuditTemplate existing = new AuditTemplate();
        existing.setId(1L);
        existing.setName("old");
        existing.setEnabled(true);
        existing.setNodes(Arrays.asList(new AuditNode(1, "n", "AUDITOR_GROUP", false)));
        when(mapper.selectById(1L)).thenReturn(existing);

        AuditTemplate out = service.update(1L, "new", null, null, false);
        assertEquals("new", out.getName());
        assertFalse(out.getEnabled());
        verify(mapper).updateById(existing);
    }

    @Test
    void delete_callsDeleteById() {
        service.delete(7L);
        verify(mapper).deleteById(7L);
    }

    @Test
    void list_byBusinessType_appliesFilter() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        service.list("ops", true);
        verify(mapper).selectList(any());
    }

    @Test
    void recommendTemplate_existing_returnsFromDb() {
        AuditTemplate t = new AuditTemplate();
        t.setId(5L);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(t));
        assertSame(t, service.recommendTemplate("ops"));
    }

    @Test
    void recommendTemplate_notFound_returnsDefaultThreeLevel() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        AuditTemplate t = service.recommendTemplate("ops");
        assertNotNull(t);
        assertEquals(3, t.getNodes().size());
        assertEquals("AUDITOR_ENTERPRISE", t.getNodes().get(0).getApproverRole());
        assertEquals("AUDITOR_REGION", t.getNodes().get(1).getApproverRole());
        assertEquals("AUDITOR_GROUP", t.getNodes().get(2).getApproverRole());
    }

    @Test
    void defaultHelpers_produceCorrectLevels() {
        AuditTemplate one = AuditTemplateService.defaultOneLevel("ops");
        assertEquals(1, one.getNodes().size());
        assertEquals("AUDITOR_GROUP", one.getNodes().get(0).getApproverRole());

        AuditTemplate two = AuditTemplateService.defaultTwoLevel("ops");
        assertEquals(2, two.getNodes().size());

        AuditTemplate three = AuditTemplateService.defaultThreeLevel("ops");
        assertEquals(3, three.getNodes().size());
    }
}
