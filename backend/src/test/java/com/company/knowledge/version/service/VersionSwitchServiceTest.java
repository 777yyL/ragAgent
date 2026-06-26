package com.company.knowledge.version.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.version.entity.VersionMeta;
import com.company.knowledge.version.mapper.VersionMetaMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link VersionSwitchService} 单元测试。覆盖：
 * <ul>
 *   <li>list：按 datasetId 过滤</li>
 *   <li>getCurrentOnline：命中 ONLINE</li>
 *   <li>create：ONLINE 创建自动串 parentId；同 env 冲突抛异常</li>
 *   <li>switchToOnline：旧 ONLINE→TEST、目标→ONLINE、parentId 串链</li>
 *   <li>switchToTest：env 改 TEST</li>
 *   <li>rollback：已是 ONLINE 抛异常；否则调 switchToOnline</li>
 *   <li>requireVersion：不存在抛异常</li>
 * </ul>
 *
 * <p>Mapper 全部 mock，不依赖真实 DB。
 */
class VersionSwitchServiceTest {

    private VersionMetaMapper mapper;
    private VersionSwitchService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(VersionMetaMapper.class);
        service = new VersionSwitchService(mapper);

        // insert 时主键回填
        when(mapper.insert(any())).thenAnswer(inv -> {
            VersionMeta v = inv.getArgument(0);
            v.setId(100L);
            return 1;
        });
        when(mapper.updateById(any())).thenReturn(1);
    }

    // ===== list =====

    @Test
    @SuppressWarnings("unchecked")
    void list_withDatasetId_returnsFiltered() {
        VersionMeta v = version(1L, "ds-1", VersionMeta.Env.ONLINE);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(v));

        List<VersionMeta> out = service.list("ds-1");

        assertEquals(1, out.size());
        assertEquals("ds-1", out.get(0).getDatasetId());
    }

    // ===== getCurrentOnline =====

    @Test
    void getCurrentOnline_found_returnsVersion() {
        VersionMeta v = version(1L, "ds-1", VersionMeta.Env.ONLINE);
        when(mapper.selectOne(any())).thenReturn(v);

        VersionMeta out = service.getCurrentOnline("ds-1");

        assertNotNull(out);
        assertEquals(VersionMeta.Env.ONLINE, out.getEnv());
    }

    @Test
    void getCurrentOnline_none_returnsNull() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertNull(service.getCurrentOnline("ds-1"));
    }

    // ===== create =====

    @Test
    void create_onlineWithExistingOnline_setsParentId() {
        // 已有 ONLINE 版本 id=5
        VersionMeta existing = version(5L, "ds-1", VersionMeta.Env.ONLINE);
        when(mapper.selectCount(any())).thenReturn(0L); // 新 env 不冲突
        when(mapper.selectOne(any())).thenReturn(existing);

        VersionMeta out = service.create("ds-1", VersionMeta.Env.ONLINE, "v2.0", "release v2");

        ArgumentCaptor<VersionMeta> cap = ArgumentCaptor.forClass(VersionMeta.class);
        verify(mapper).insert(cap.capture());
        assertEquals(VersionMeta.Env.ONLINE, cap.getValue().getEnv());
        assertEquals(Long.valueOf(5L), cap.getValue().getParentId(),
                "新 ONLINE 的 parentId 应指向旧 ONLINE");
        assertNotNull(cap.getValue().getPublishedAt());
        assertEquals("v2.0", out.getVersionLabel());
    }

    @Test
    void create_duplicateEnv_throwsConflict() {
        when(mapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> service.create("ds-1", VersionMeta.Env.ONLINE, "v2", ""));
        assertEquals(VersionSwitchService.CODE_ENV_CONFLICT, ex.getCode());
    }

    // ===== switchToOnline =====

    @Test
    void switchToOnline_demotesOldOnlineAndPromotesTarget() {
        VersionMeta old = version(1L, "ds-1", VersionMeta.Env.ONLINE);
        VersionMeta target = version(2L, "ds-1", VersionMeta.Env.TEST);
        when(mapper.selectById(2L)).thenReturn(target);
        // getCurrentOnline 查找 ds-1 的 ONLINE
        when(mapper.selectOne(any())).thenReturn(old);

        VersionMeta out = service.switchToOnline(2L);

        ArgumentCaptor<VersionMeta> cap = ArgumentCaptor.forClass(VersionMeta.class);
        // 两次 updateById：旧版本→TEST，目标→ONLINE
        verify(mapper, times(2)).updateById(cap.capture());

        // 第一次 update 调用的是旧版本
        assertEquals(Long.valueOf(1L), cap.getAllValues().get(0).getId());
        assertEquals(VersionMeta.Env.TEST, cap.getAllValues().get(0).getEnv());
        // 第二次 update 调用的是目标版本
        assertEquals(Long.valueOf(2L), cap.getAllValues().get(1).getId());
        assertEquals(VersionMeta.Env.ONLINE, cap.getAllValues().get(1).getEnv());
        assertNotNull(cap.getAllValues().get(1).getParentId());
        assertEquals(Long.valueOf(1L), cap.getAllValues().get(1).getParentId());

        assertEquals(VersionMeta.Env.ONLINE, out.getEnv());
    }

    @Test
    void switchToOnline_targetAlreadyOnline_onlyUpdatesItself() {
        // 目标本身已是 ONLINE，且无其它版本——只更新自身
        VersionMeta target = version(2L, "ds-1", VersionMeta.Env.ONLINE);
        when(mapper.selectById(2L)).thenReturn(target);
        when(mapper.selectOne(any())).thenReturn(target); // getCurrentOnline 返回自己

        service.switchToOnline(2L);

        verify(mapper, times(1)).updateById(any());
        // 没有把目标自身降级
        ArgumentCaptor<VersionMeta> cap = ArgumentCaptor.forClass(VersionMeta.class);
        verify(mapper).updateById(cap.capture());
        assertEquals(VersionMeta.Env.ONLINE, cap.getValue().getEnv());
    }

    @Test
    void switchToOnline_notFound_throws() {
        when(mapper.selectById(anyLong())).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service.switchToOnline(999L));
        assertEquals(VersionSwitchService.CODE_VERSION_NOT_FOUND, ex.getCode());
    }

    // ===== switchToTest =====

    @Test
    void switchToTest_setsEnvTest() {
        VersionMeta target = version(3L, "ds-1", VersionMeta.Env.ONLINE);
        when(mapper.selectById(3L)).thenReturn(target);

        VersionMeta out = service.switchToTest(3L);

        ArgumentCaptor<VersionMeta> cap = ArgumentCaptor.forClass(VersionMeta.class);
        verify(mapper).updateById(cap.capture());
        assertEquals(VersionMeta.Env.TEST, cap.getValue().getEnv());
        assertEquals(VersionMeta.Env.TEST, out.getEnv());
    }

    // ===== rollback =====

    @Test
    void rollback_alreadyOnline_throws() {
        VersionMeta v = version(4L, "ds-1", VersionMeta.Env.ONLINE);
        when(mapper.selectById(4L)).thenReturn(v);

        BizException ex = assertThrows(BizException.class,
                () -> service.rollback(4L));
        assertEquals(VersionSwitchService.CODE_NO_PARENT, ex.getCode());
    }

    @Test
    void rollback_testVersion_delegatesToSwitchToOnline() {
        VersionMeta target = version(4L, "ds-1", VersionMeta.Env.TEST);
        VersionMeta old = version(1L, "ds-1", VersionMeta.Env.ONLINE);
        when(mapper.selectById(4L)).thenReturn(target);
        when(mapper.selectOne(any())).thenReturn(old);

        VersionMeta out = service.rollback(4L);

        assertEquals(VersionMeta.Env.ONLINE, out.getEnv());
        verify(mapper, times(2)).updateById(any());
    }

    // ===== 辅助 =====

    private VersionMeta version(long id, String datasetId, String env) {
        VersionMeta v = new VersionMeta();
        v.setId(id);
        v.setDatasetId(datasetId);
        v.setEnv(env);
        v.setVersionLabel("v" + id);
        return v;
    }
}
