package com.company.knowledge.permission.aspect;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.permission.annotation.OperationLog;
import com.company.knowledge.permission.entity.OperationLogEntry;
import com.company.knowledge.permission.service.OperationLogWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OperationLogAspect} 单元测试。
 *
 * <p>覆盖：成功路径写入 SUCCESS；异常路径写入 FAIL 且透传；resourceId 按 paramName 提取；
 * 未登录用户写入 anonymous。
 */
class OperationLogAspectTest {

    private OperationLogWriter writer;
    private ObjectMapper objectMapper;
    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() {
        writer = Mockito.mock(OperationLogWriter.class);
        objectMapper = new ObjectMapper();
        aspect = new OperationLogAspect(writer, objectMapper);
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void around_success_writesSuccessLog() throws Throwable {
        setUser("p1", "tester");
        OperationLog ann = annotationOn("createDoc");
        ProceedingJoinPoint pjp = mockJoinPoint(ann, new Object[]{"doc-title"});

        when(pjp.proceed()).thenReturn(42L);

        Object out = aspect.around(pjp, ann);

        assertEquals(42L, out);
        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(writer, times(1)).writeAsync(captor.capture());
        OperationLogEntry entry = captor.getValue();
        assertEquals("CREATE_DOC", entry.getAction());
        assertEquals("DOC", entry.getResourceType());
        assertEquals("SUCCESS", entry.getResult());
        assertEquals("p1", entry.getUserId());
        assertEquals("tester", entry.getUsername());
        assertNull(entry.getErrorMsg());
    }

    @Test
    void around_exception_writesFailLogAndRethrows() throws Throwable {
        setUser("p1", "tester");
        OperationLog ann = annotationOn("createDoc");
        ProceedingJoinPoint pjp = mockJoinPoint(ann, new Object[]{"doc-title"});

        RuntimeException bizError = new RuntimeException("boom");
        when(pjp.proceed()).thenThrow(bizError);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> aspect.around(pjp, ann));
        assertSame(bizError, thrown);

        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(writer, times(1)).writeAsync(captor.capture());
        OperationLogEntry entry = captor.getValue();
        assertEquals("FAIL", entry.getResult());
        assertNotNull(entry.getErrorMsg());
        assertTrue(entry.getErrorMsg().contains("boom"));
    }

    @Test
    void around_resourceIdParamExtracted() throws Throwable {
        setUser("p1", "tester");
        OperationLog ann = annotationOn("deleteDoc");
        // 第二个参数名为 docId，值为 "abc-123"
        ProceedingJoinPoint pjp = mockJoinPoint(ann, new Object[]{"ignored", "abc-123"});

        when(pjp.proceed()).thenReturn(null);

        aspect.around(pjp, ann);

        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(writer, times(1)).writeAsync(captor.capture());
        assertEquals("abc-123", captor.getValue().getResourceId());
    }

    @Test
    void around_anonymousUser_userIdFallback() throws Throwable {
        UserContext.clear();
        OperationLog ann = annotationOn("createDoc");
        ProceedingJoinPoint pjp = mockJoinPoint(ann, new Object[]{"x"});

        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp, ann);

        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(writer, times(1)).writeAsync(captor.capture());
        assertEquals("anonymous", captor.getValue().getUserId());
    }

    @Test
    void around_writerThrows_aspectSwallows() throws Throwable {
        setUser("p1", "tester");
        OperationLog ann = annotationOn("createDoc");
        ProceedingJoinPoint pjp = mockJoinPoint(ann, new Object[]{"x"});

        when(pjp.proceed()).thenReturn("ok");
        // writer.writeAsync 内部 catch 异常，但即便它抛出来也不应影响业务
        doThrow(new RuntimeException("async-oom")).when(writer).writeAsync(any());

        Object out = aspect.around(pjp, ann);
        // 业务返回正常
        assertEquals("ok", out);
    }

    // --- helpers ---

    static class Tag {
        @OperationLog(action = "CREATE_DOC", resourceType = "DOC")
        public String createDoc(String title) {
            return "ok";
        }

        @OperationLog(action = "DELETE_DOC", resourceType = "DOC", resourceIdParam = "docId")
        public String deleteDoc(String ignored, String docId) {
            return "ok";
        }
    }

    private OperationLog annotationOn(String methodName) {
        try {
            Method m;
            if ("deleteDoc".equals(methodName)) {
                m = Tag.class.getMethod(methodName, String.class, String.class);
            } else {
                m = Tag.class.getMethod(methodName, String.class);
            }
            return m.getAnnotation(OperationLog.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private ProceedingJoinPoint mockJoinPoint(OperationLog ann, Object[] args) {
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature sig = Mockito.mock(MethodSignature.class);
        try {
            if (args.length == 2) {
                when(sig.getMethod()).thenReturn(Tag.class.getMethod("deleteDoc", String.class, String.class));
            } else {
                when(sig.getMethod()).thenReturn(Tag.class.getMethod("createDoc", String.class));
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(args);
        // deleteDoc 有两个参数
        String[] paramNames;
        if (args.length == 2) {
            paramNames = new String[]{"ignored", "docId"};
        } else {
            paramNames = new String[]{"title"};
        }
        when(sig.getParameterNames()).thenReturn(paramNames);
        return pjp;
    }

    private void setUser(String personId, String name) {
        UserContext.set(new UserContext.CurrentUser(
                personId, name, "dept", "@root@",
                new HashSet<>(Collections.singletonList("EDITOR"))));
    }
}
