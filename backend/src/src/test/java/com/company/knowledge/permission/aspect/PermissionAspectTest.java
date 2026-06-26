package com.company.knowledge.permission.aspect;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.permission.annotation.RequiresPermission;
import com.company.knowledge.permission.entity.PermissionPolicy;
import com.company.knowledge.permission.service.PermissionPolicyService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link PermissionAspect} 单元测试。
 *
 * <p>用真实 {@link RequiresPermissionTag} 方法上的注解，
 * 验证：admin 放行 / 普通角色命中策略放行 / 普通角色未命中抛 4031 / 未登录抛 401。
 */
class PermissionAspectTest {

    private PermissionPolicyService policyService;
    private PermissionAspect aspect;

    @BeforeEach
    void setUp() {
        policyService = Mockito.mock(PermissionPolicyService.class);
        aspect = new PermissionAspect(policyService);
    }

    @AfterEach
    void clearThread() {
        UserContext.clear();
    }

    @Test
    void check_notLoggedIn_throws401() {
        UserContext.clear();
        RequiresPermission ann = annotationOn("adminPath");
        BizException ex = assertThrows(BizException.class,
                () -> aspect.check(mockJoinPoint(ann), ann));
        assertEquals(401, ex.getCode());
    }

    @Test
    void check_adminRole_alwaysPass() throws Throwable {
        setUser("p1", new HashSet<>(Collections.singletonList("ADMIN")));
        ProceedingJoinPoint pjp = mockJoinPoint(annotationOn("adminPath"));
        when(pjp.proceed()).thenReturn("ok");

        Object out = aspect.check(pjp, annotationOn("adminPath"));
        assertEquals("ok", out);
        // ADMIN 路径不应查策略
        verify(policyService, never()).listBySubject(any(), any());
    }

    @Test
    void check_editorWithMatchingPolicy_pass() throws Throwable {
        setUser("p1", new HashSet<>(Collections.singletonList("EDITOR")));
        PermissionPolicy p = new PermissionPolicy();
        p.setObjectType("CATEGORY");
        p.setActions(new String[]{"VIEW", "EDIT"});
        when(policyService.listBySubject("ROLE", "EDITOR"))
                .thenReturn(Collections.singletonList(p));

        ProceedingJoinPoint pjp = mockJoinPoint(annotationOn("editorEditCategory"));
        when(pjp.proceed()).thenReturn("ok");

        Object out = aspect.check(pjp, annotationOn("editorEditCategory"));
        assertEquals("ok", out);
    }

    @Test
    void check_editorWithoutMatchingPolicy_throws4031() {
        setUser("p1", new HashSet<>(Collections.singletonList("EDITOR")));
        when(policyService.listBySubject("ROLE", "EDITOR"))
                .thenReturn(Collections.emptyList());

        BizException ex = assertThrows(BizException.class,
                () -> aspect.check(mockJoinPoint(annotationOn("editorEditCategory")),
                        annotationOn("editorEditCategory")));
        assertEquals(4031, ex.getCode());
    }

    @Test
    void check_viewerWithWrongAction_throws4031() {
        setUser("p1", new HashSet<>(Collections.singletonList("VIEWER")));
        // VIEWER 只有 VIEW 权限，注解需要 EDIT
        PermissionPolicy p = new PermissionPolicy();
        p.setObjectType("CATEGORY");
        p.setActions(new String[]{"VIEW"});
        when(policyService.listBySubject("ROLE", "VIEWER"))
                .thenReturn(Collections.singletonList(p));

        BizException ex = assertThrows(BizException.class,
                () -> aspect.check(mockJoinPoint(annotationOn("editorEditCategory")),
                        annotationOn("editorEditCategory")));
        assertEquals(4031, ex.getCode());
    }

    // --- helpers ---

    /**
     * 占位类，承载 {@link RequiresPermission} 注解用于测试反射获取。
     */
    static class RequiresPermissionTag {
        @RequiresPermission(resourceType = "CATEGORY", action = "EDIT")
        public String adminPath() {
            return "ok";
        }

        @RequiresPermission(resourceType = "CATEGORY", action = "EDIT")
        public String editorEditCategory() {
            return "ok";
        }
    }

    private RequiresPermission annotationOn(String methodName) {
        try {
            Method m = RequiresPermissionTag.class.getMethod(methodName);
            return m.getAnnotation(RequiresPermission.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private ProceedingJoinPoint mockJoinPoint(RequiresPermission ann) {
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature sig = Mockito.mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(RequiresPermissionTag.class.getMethods()[0]);
        when(pjp.getSignature()).thenReturn(sig);
        return pjp;
    }

    private void setUser(String personId, Set<String> roles) {
        UserContext.set(new UserContext.CurrentUser(
                personId, "tester", "dept", "@root@", roles));
    }
}
