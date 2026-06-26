package com.company.knowledge.audit.service;

import com.company.knowledge.audit.entity.AuditInstance;
import com.company.knowledge.audit.mapper.AuditInstanceMapper;
import com.company.knowledge.chunk.ChunkService;
import com.company.knowledge.permission.service.OperationLogWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * {@link PublishService} 单元测试。ChunkService / Mapper / LogWriter 全部 mock。
 */
class PublishServiceTest {

    private ChunkService chunkService;
    private AuditInstanceMapper instanceMapper;
    private OperationLogWriter logWriter;
    private PublishService service;

    @BeforeEach
    void setUp() {
        chunkService = Mockito.mock(ChunkService.class);
        instanceMapper = Mockito.mock(AuditInstanceMapper.class);
        logWriter = Mockito.mock(OperationLogWriter.class);
        service = new PublishService(chunkService, instanceMapper, logWriter);
    }

    @Test
    void publish_normal_setsAvailabilityAndUpdatesInstance() {
        // 准备 instance
        AuditInstance inst = new AuditInstance();
        inst.setId(1L);
        inst.setDocId(50L);
        inst.setStatus("APPROVED");
        when(instanceMapper.selectById(1L)).thenReturn(inst);
        when(instanceMapper.updateById(any())).thenReturn(1);

        // ChunkService.list 现在返回 Map<String,Object>，chunks 字段是 List<Map>
        Map<String, Object> chunksResp = chunksMap(chunkMap("c1"), chunkMap("c2"));
        when(chunkService.list(anyString(), anyString(), isNull())).thenReturn(chunksResp);

        service.publish(1L);

        // 验证 setAvailability 被调用
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(chunkService).setAvailability(anyString(), anyString(), cap.capture(), eq(true));
        assertEquals(2, cap.getValue().size());
        assertTrue(cap.getValue().contains("c1"));
        assertTrue(cap.getValue().contains("c2"));

        // instance 状态 → PUBLISHED
        assertEquals("PUBLISHED", inst.getStatus());
        verify(instanceMapper).updateById(inst);

        // 操作日志被异步写入
        verify(logWriter).writeAsync(any());
    }

    @Test
    void publish_emptyChunks_skipsAvailabilityButStillUpdatesInstance() {
        AuditInstance inst = new AuditInstance();
        inst.setId(1L);
        inst.setDocId(50L);
        when(instanceMapper.selectById(1L)).thenReturn(inst);

        Map<String, Object> empty = chunksMap(); // chunks 为空
        when(chunkService.list(anyString(), anyString(), isNull())).thenReturn(empty);

        service.publish(1L);

        verify(chunkService, never()).setAvailability(anyString(), anyString(), anyList(), anyBoolean());
        // 状态仍然推进到 PUBLISHED
        assertEquals("PUBLISHED", inst.getStatus());
        verify(instanceMapper).updateById(inst);
        verify(logWriter).writeAsync(any());
    }

    @Test
    void publish_instanceNotFound_throws() {
        when(instanceMapper.selectById(999L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.publish(999L));
    }

    @Test
    void unpublish_normal_setsAvailableFalse() {
        Map<String, Object> chunksResp = chunksMap(chunkMap("c1"));
        when(chunkService.list(anyString(), anyString(), isNull())).thenReturn(chunksResp);

        service.unpublish("ds1", "doc1");

        verify(chunkService).setAvailability(eq("ds1"), eq("doc1"), anyList(), eq(false));
        verify(logWriter).writeAsync(any());
    }

    @Test
    void publishWithDatasetId_callsSetAvailability() {
        Map<String, Object> chunksResp = chunksMap(chunkMap("c1"), chunkMap("c2"));
        when(chunkService.list(anyString(), anyString(), isNull())).thenReturn(chunksResp);

        service.publish("ds1", 100L);

        verify(chunkService).setAvailability(eq("ds1"), eq("100"), anyList(), eq(true));
        verify(logWriter).writeAsync(any());
    }

    @Test
    void listChunkIds_handlesBothResponseShapes() {
        // resp 顶层没有 data，直接 chunks
        Map<String, Object> direct = chunksMap(chunkMap("x1"));
        when(chunkService.list(anyString(), anyString(), isNull())).thenReturn(direct);

        service.unpublish("ds", "doc");

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(chunkService).setAvailability(anyString(), anyString(), cap.capture(), eq(false));
        assertEquals(1, cap.getValue().size());
        assertEquals("x1", cap.getValue().get(0));
    }

    // ===== helpers =====

    /** 构造 chunk Map，仅含 id 字段。 */
    private static Map<String, Object> chunkMap(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        return m;
    }

    /** 构造 ChunkService.list 的返回值：{chunks: [...], total: N}。 */
    private static Map<String, Object> chunksMap(Map<String, Object>... chunks) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("chunks", Arrays.asList(chunks));
        resp.put("total", chunks.length);
        return resp;
    }
}
