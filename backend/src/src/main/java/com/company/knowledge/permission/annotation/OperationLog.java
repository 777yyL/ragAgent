package com.company.knowledge.permission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式审计日志注解（被 {@code OperationLogAspect} 拦截）。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * @OperationLog(action = "CREATE_DOC", resourceType = "DOC")
 * @PostMapping("/docs")
 * public Result<Long> create(@RequestBody DocDto dto) { ... }
 * }</pre>
 *
 * <p>记录内容：
 *
 * <ul>
 *   <li>用户信息（UserContext.personId / personName）</li>
 *   <li>请求 IP（RequestContextHolder）</li>
 *   <li>{@link #action()} + {@link #resourceType()}</li>
 *   <li>resource_id：从方法入参中按 {@link #resourceIdParam()} 名称查找</li>
 *   <li>request_body：方法入参 JSON 摘要</li>
 *   <li>result：SUCCESS / FAIL（异常时为 FAIL）</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 动作编码（与业务约定一致，如 CREATE_DOC / AUDIT_PASS / SEARCH） */
    String action();

    /** 资源类型，可选 */
    String resourceType() default "";

    /**
     * 资源 ID 参数名。Aspect 会按参数名从 joinPoint.args 中查找。
     *
     * <p>留空（默认）表示不记录 resource_id。
     */
    String resourceIdParam() default "";
}
