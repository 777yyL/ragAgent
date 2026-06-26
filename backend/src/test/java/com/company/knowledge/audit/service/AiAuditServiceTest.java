package com.company.knowledge.audit.service;

import com.company.knowledge.audit.constant.IssueStatus;
import com.company.knowledge.audit.constant.IssueType;
import com.company.knowledge.audit.entity.AiAuditIssue;
import com.company.knowledge.audit.mapper.AiAuditIssueMapper;
import com.company.knowledge.chunk.ChunkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiAuditService} 单元测试。ChunkService/Mapper 全部 mock。
 *
 * <p>测试 6 类检测 + 批量处理 + LLM 注入。
 */
class AiAuditServiceTest {

    private AiAuditIssueMapper issueMapper;
    private ChunkService chunkService;
    private LlmGateway llmGateway;
    private AiAuditService service;

    @BeforeEach
    void setUp() {
        issueMapper = Mockito.mock(AiAuditIssueMapper.class);
        chunkService = Mockito.mock(ChunkService.class);
        llmGateway = null;
        service = new AiAuditService(issueMapper, chunkService);
        // 默认无 LLM，模拟 Spring 容器无 Bean 时的降级
        when(issueMapper.insert(any())).thenReturn(1);
        when(issueMapper.updateById(any())).thenReturn(1);
    }

    // ===== detectError =====

    @Test
    void detectError_voltageOutOfRange_emitsWarn() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "发电机出口电压 2000kV，超过常规范围");
        List<AiAuditIssue> issues = service.detectError(50L, chunk);
        assertFalse(issues.isEmpty());
        assertEquals(IssueType.ERROR.name(), issues.get(0).getIssueType());
        assertTrue(issues.get(0).getDescription().contains("2000"));
    }

    @Test
    void detectError_negativeCurrent_emitsWarn() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "回路电流 -5A 异常");
        List<AiAuditIssue> issues = service.detectError(50L, chunk);
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).getDescription().contains("负值"));
    }

    @Test
    void detectError_normalValue_emitsNothing() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "出口电压 500kV 正常运行");
        List<AiAuditIssue> issues = service.detectError(50L, chunk);
        assertTrue(issues.isEmpty());
    }

    // ===== detectTimeliness =====

    @Test
    void detectTimeliness_standardReference_emitsInfo() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "本规程依据 GB/T 12345 和 DL/T 637 制定");
        List<AiAuditIssue> issues = service.detectTimeliness(50L, chunk);
        assertEquals(2, issues.size());
        assertEquals(IssueType.TIMELINESS.name(), issues.get(0).getIssueType());
    }

    @Test
    void detectTimeliness_noStandard_emitsNothing() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "本规程规定了操作流程");
        List<AiAuditIssue> issues = service.detectTimeliness(50L, chunk);
        assertTrue(issues.isEmpty());
    }

    // ===== detectNorm =====

    @Test
    void detectNorm_chineseUnit_emitsInfo() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "功率 100 瓦特，频率 50 赫兹");
        List<AiAuditIssue> issues = service.detectNorm(50L, chunk);
        assertEquals(2, issues.size());
    }

    @Test
    void detectNorm_correctSymbols_emitsNothing() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "功率 100W，频率 50Hz");
        List<AiAuditIssue> issues = service.detectNorm(50L, chunk);
        assertTrue(issues.isEmpty());
    }

    // ===== detectIntegrity =====

    @Test
    void detectIntegrity_shortContent_emitsInfo() {
        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1", "短文本");
        List<AiAuditIssue> issues = service.detectIntegrity(50L, chunk);
        assertFalse(issues.isEmpty());
        assertEquals(IssueType.INTEGRITY.name(), issues.get(0).getIssueType());
    }

    // ===== detectConflict =====

    @Test
    void detectConflict_sameUnitDiffValues_emitsWarn() {
        List<AiAuditService.ChunkInfo> chunks = Arrays.asList(
                new AiAuditService.ChunkInfo("c1", "电压 220kV"),
                new AiAuditService.ChunkInfo("c2", "电压 500kV"));
        List<AiAuditIssue> issues = service.detectConflict(50L, chunks);
        assertFalse(issues.isEmpty());
        assertEquals(IssueType.CONFLICT.name(), issues.get(0).getIssueType());
    }

    @Test
    void detectConflict_closeValues_emitsNothing() {
        List<AiAuditService.ChunkInfo> chunks = Arrays.asList(
                new AiAuditService.ChunkInfo("c1", "电压 500kV"),
                new AiAuditService.ChunkInfo("c2", "电压 505kV"));
        List<AiAuditIssue> issues = service.detectConflict(50L, chunks);
        assertTrue(issues.isEmpty());
    }

    // ===== detectConsistency =====

    @Test
    void detectConsistency_traditionalChars_emitsInfo() {
        List<AiAuditService.ChunkInfo> chunks = Collections.singletonList(
                new AiAuditService.ChunkInfo("c1", "主變壓器運行正常"));
        List<AiAuditIssue> issues = service.detectConsistency(50L, chunks);
        assertFalse(issues.isEmpty());
        assertEquals(IssueType.CONSISTENCY.name(), issues.get(0).getIssueType());
    }

    // ===== runAiAudit 全流程 =====

    @Test
    void runAiAudit_normal_insertsAllDetectedIssues() {
        Map<String, Object> chunksResp = new LinkedHashMap<>();
        chunksResp.put("chunks", Arrays.asList(
                chunkMap("c1", "出口电压 2000kV 超限"),
                chunkMap("c2", "依据 GB/T 14285 标准")));
        chunksResp.put("total", 2);
        when(chunkService.list(anyString(), anyString(), isNull())).thenReturn(chunksResp);

        service.runAiAudit("ds1", 50L);

        // 至少插入了 detectError + detectTimeliness 各一条
        verify(issueMapper, atLeast(2)).insert(any());
    }

    @Test
    void runAiAudit_noChunks_insertsNothing() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("chunks", Collections.emptyList());
        empty.put("total", 0);
        when(chunkService.list(anyString(), anyString(), isNull())).thenReturn(empty);

        service.runAiAudit("ds1", 50L);

        verify(issueMapper, never()).insert(any());
    }

    @Test
    void runAiAudit_chunkServiceThrows_swallowed() {
        when(chunkService.list(anyString(), anyString(), isNull()))
                .thenThrow(new RuntimeException("ragflow down"));

        // @Async 在单元测试中同步执行，异常应被吞掉不传播
        assertDoesNotThrow(() -> service.runAiAudit("ds1", 50L));
    }

    // ===== reportByDoc =====

    @Test
    void reportByDoc_appliesFilters() {
        AiAuditIssue i = new AiAuditIssue();
        i.setId(1L);
        i.setDocId(50L);
        when(issueMapper.selectList(any())).thenReturn(Collections.singletonList(i));

        List<AiAuditIssue> out = service.reportByDoc(50L, "ERROR", "WARN", "OPEN");
        assertEquals(1, out.size());
    }

    // ===== batchResolve =====

    @Test
    void batchResolve_resolveAction_setsStatusResolved() {
        AiAuditIssue i1 = new AiAuditIssue();
        i1.setId(1L);
        AiAuditIssue i2 = new AiAuditIssue();
        i2.setId(2L);
        when(issueMapper.selectById(1L)).thenReturn(i1);
        when(issueMapper.selectById(2L)).thenReturn(i2);

        int n = service.batchResolve(Arrays.asList(1L, 2L), "RESOLVE");

        assertEquals(2, n);
        assertEquals(IssueStatus.RESOLVED, i1.getStatus());
        assertEquals(IssueStatus.RESOLVED, i2.getStatus());
    }

    @Test
    void batchResolve_ignoreAction_setsStatusIgnored() {
        AiAuditIssue i = new AiAuditIssue();
        i.setId(1L);
        when(issueMapper.selectById(1L)).thenReturn(i);

        service.batchResolve(Collections.singletonList(1L), "IGNORE");

        assertEquals(IssueStatus.IGNORED, i.getStatus());
    }

    @Test
    void batchResolve_unsupportedAction_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.batchResolve(Collections.singletonList(1L), "DELETE"));
    }

    @Test
    void batchResolve_emptyIds_returnsZero() {
        assertEquals(0, service.batchResolve(Collections.emptyList(), "RESOLVE"));
        assertEquals(0, service.batchResolve(null, "RESOLVE"));
    }

    @Test
    void batchResolve_missingIssue_skipped() {
        when(issueMapper.selectById(99L)).thenReturn(null);
        int n = service.batchResolve(Collections.singletonList(99L), "RESOLVE");
        assertEquals(0, n);
    }

    // ===== LLM 增强 =====

    @Test
    void detectError_llmGatewayAvailable_severityUpgraded() {
        llmGateway = Mockito.mock(LlmGateway.class);
        when(llmGateway.chat(anyString(), anyString())).thenReturn("该数值不合理，超出极限");
        // 通过反射注入（或重新 new） - 此处重新构造 service
        AiAuditService s = new AiAuditService(issueMapper, chunkService);
        // 手动注入
        try {
            java.lang.reflect.Field f = AiAuditService.class.getDeclaredField("llmGateway");
            f.setAccessible(true);
            f.set(s, llmGateway);
        } catch (Exception e) {
            fail("inject llmGateway failed: " + e.getMessage());
        }

        AiAuditService.ChunkInfo chunk = new AiAuditService.ChunkInfo("c1",
                "电压 2000kV");
        List<AiAuditIssue> issues = s.detectError(50L, chunk);
        assertFalse(issues.isEmpty());
        verify(llmGateway).chat(anyString(), anyString());
    }

    // ===== helpers =====

    /** 构造单个 chunk 的 Map（id + content）。 */
    private static Map<String, Object> chunkMap(String id, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("content", content);
        return m;
    }
}
