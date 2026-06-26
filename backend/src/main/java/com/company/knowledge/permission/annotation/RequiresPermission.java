package com.company.knowledge.permission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式权限校验注解（被 {@code PermissionAspect} 拦截）。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * @RequiresPermission(resourceType = "CATEGORY", action = "EDIT")
 * @PostMapping("/categories/{id}")
 * public Result<Void> update(@PathVariable Long id, @RequestBody CategoryDto dto) { ... }
 * }</pre>
 *
 * <p>校验规则（按顺序短路）：
 *
 * <ol>
 *   <li>从 {@code UserContext} 取当前用户的 roles</li>
 *   <li>若 roles 含 {@code ADMIN}，直接放行</li>
 *   <li>否则查 {@code PermissionPolicy} 是否存在任一策略，
 *       其 {@code actions} 包含 {@link #action()}，
 *       且 subject_type=ROLE，subject_id ∈ roles</li>
 *   <li>不通过抛 {@code BizException(4031, "permission denied")}</li>
 * </ol>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 资源类型，对齐 {@code permission_policy.object_type}：
     * DATASET / CATEGORY / TAG / DOC。
     */
    String resourceType();

    /**
     * 动作，对齐 {@code permission_policy.actions} 元素：
     * VIEW / SEARCH / EDIT / DELETE / AUDIT / PUBLISH / EXPORT。
     */
    String action();
}
