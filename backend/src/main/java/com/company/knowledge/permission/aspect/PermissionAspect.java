package com.company.knowledge.permission.aspect;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.permission.annotation.RequiresPermission;
import com.company.knowledge.permission.entity.PermissionPolicy;
import com.company.knowledge.permission.service.PermissionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * {@link RequiresPermission} 注解拦截切面。
 *
 * <p>简化版逻辑：
 *
 * <ol>
 *   <li>未登录 → {@code BizException(401, "not logged in")}</li>
 *   <li>角色含 {@code ADMIN} → 放行</li>
 *   <li>角色与注解 {@code action} 匹配任一策略 → 放行</li>
 *   <li>否则 → {@code BizException(4031, "permission denied")}</li>
 * </ol>
 *
 * <p>资源对象维度的精确校验（如 doc_id）由调用方在 Service 内自查，
 * 此切面仅做「角色 - 动作」级粗粒度校验。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    /** ADMIN 角色 code，全局放行 */
    public static final String ADMIN_ROLE = "ADMIN";

    /** 业务码：权限不足 */
    public static final int CODE_FORBIDDEN = 4031;

    /** 业务码：未登录 */
    public static final int CODE_UNAUTHORIZED = 401;

    private final PermissionPolicyService policyService;

    /**
     * 拦截标注 {@link RequiresPermission} 的方法。
     *
     * @param pjp    Spring AOP join point
     * @param annotation 注解实例（Spring 自动注入）
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常透传
     */
    @Around("@annotation(annotation)")
    public Object check(ProceedingJoinPoint pjp, RequiresPermission annotation) throws Throwable {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null) {
            throw BizException.of(CODE_UNAUTHORIZED, "not logged in");
        }
        Set<String> roles = user.safeRoles();
        String resourceType = annotation.resourceType();
        String action = annotation.action();

        if (roles.contains(ADMIN_ROLE)) {
            log.debug("permission ok (admin), personId={}, resource={}, action={}",
                    user.getPersonId(), resourceType, action);
            return pjp.proceed();
        }

        if (hasPermission(roles, resourceType, action)) {
            log.debug("permission ok, personId={}, resource={}, action={}, roles={}",
                    user.getPersonId(), resourceType, action, roles);
            return pjp.proceed();
        }

        log.warn("permission denied, personId={}, resource={}, action={}, roles={}",
                user.getPersonId(), resourceType, action, roles);
        throw BizException.of(CODE_FORBIDDEN, "permission denied");
    }

    /**
     * 检查角色集合是否拥有某 resource_type + action 的任一策略。
     *
     * <p>遍历 roles，对每个 role 查 {@code subject_type=ROLE, subject_id=roleCode}，
     * 检查是否存在策略的 {@code actions} 包含目标 action 且
     * {@code object_type} 等于目标 resourceType。
     */
    private boolean hasPermission(Set<String> roles, String resourceType, String action) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (String roleCode : roles) {
            List<PermissionPolicy> policies = policyService.listBySubject("ROLE", roleCode);
            if (policies == null || policies.isEmpty()) {
                continue;
            }
            for (PermissionPolicy p : policies) {
                if (!resourceType.equals(p.getObjectType())) {
                    continue;
                }
                String[] actions = p.getActions();
                if (actions == null) {
                    continue;
                }
                for (String a : actions) {
                    if (action.equals(a)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
