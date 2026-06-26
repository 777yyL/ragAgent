package com.company.knowledge.permission.aspect;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.permission.annotation.OperationLog;
import com.company.knowledge.permission.entity.OperationLogEntry;
import com.company.knowledge.permission.service.OperationLogWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * {@link OperationLog} 注解拦截切面（等保三级审计）。
 *
 * <p>{@code @Around} 包裹目标方法：
 *
 * <ol>
 *   <li>记录 before（方法入参 JSON）</li>
 *   <li>执行原方法，捕获返回值 / 异常</li>
 *   <li>组装 OperationLog 实体（含 user / ip / action / result）</li>
 *   <li>调用 {@link OperationLogWriter#writeAsync} 异步落库</li>
 *   <li>原异常透传</li>
 * </ol>
 *
 * <p>审计失败不影响业务：{@code OperationLogWriter} 内部 catch 全部异常。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAIL = "FAIL";

    private final OperationLogWriter writer;
    private final ObjectMapper objectMapper;

    /**
     * 拦截标注 {@link OperationLog} 的方法。
     *
     * @param pjp        Spring AOP join point
     * @param annotation 注解实例
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常透传
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint pjp, OperationLog annotation) throws Throwable {
        // 1. 收集 before
        String before = serializeArgs(pjp);

        Object retval = null;
        String errorMsg = null;
        String result = RESULT_SUCCESS;
        Throwable thrown = null;

        try {
            retval = pjp.proceed();
            return retval;
        } catch (Throwable t) {
            result = RESULT_FAIL;
            errorMsg = t.getClass().getSimpleName() + ": " + t.getMessage();
            thrown = t;
            throw thrown;
        } finally {
            // 2. 组装并异步写入
            try {
                OperationLogEntry logEntry = buildEntry(pjp, annotation, before, retval, result, errorMsg);
                writer.writeAsync(logEntry);
            } catch (Exception e) {
                // 审计组装失败不影响业务
                log.warn("operation log build failed: {}", e.getMessage());
            }
        }
    }

    /**
     * 组装 {@link OperationLogEntry}。
     */
    private OperationLogEntry buildEntry(ProceedingJoinPoint pjp, OperationLog annotation,
                                         String before, Object retval,
                                         String result, String errorMsg) {
        OperationLogEntry entry = new OperationLogEntry();
        entry.setAction(annotation.action());
        entry.setResourceType(emptyToNull(annotation.resourceType()));
        entry.setResourceId(extractResourceId(pjp, annotation.resourceIdParam()));
        entry.setBeforeSnapshot(before);
        entry.setAfterSnapshot(serialize(retval));
        entry.setResult(result);
        entry.setErrorMsg(errorMsg);
        entry.setCreatedAt(LocalDateTime.now());

        // 用户信息（未登录也允许审计）
        UserContext.CurrentUser user = UserContext.get();
        if (user != null) {
            entry.setUserId(user.getPersonId());
            entry.setUsername(user.getPersonName());
        } else {
            entry.setUserId("anonymous");
        }

        // 请求 IP
        entry.setRequestIp(resolveClientIp());

        // request_body：简化为方法入参 JSON（与 before 同）
        entry.setRequestBody(before);

        return entry;
    }

    /**
     * 按 {@code paramName} 从 joinPoint 入参中查找 resource_id。
     *
     * <p>若 paramName 为空或未找到，返回 null。
     */
    private String extractResourceId(ProceedingJoinPoint pjp, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] paramNames = sig.getParameterNames();
        if (paramNames == null) {
            return null;
        }
        Object[] args = pjp.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            if (paramName.equals(paramNames[i]) && args[i] != null) {
                return args[i].toString();
            }
        }
        return null;
    }

    /**
     * 序列化方法入参为 JSON（异常时退化为 Arrays.toString）。
     */
    private String serializeArgs(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            return Arrays.toString(args);
        }
    }

    /**
     * 序列化任意对象为 JSON（null/异常返回 null）。
     */
    private String serialize(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 RequestContextHolder 取当前请求 IP。
     *
     * <p>优先 {@code X-Forwarded-For}，否则取 {@code remoteAddr}。
     * 非 HTTP 上下文（如定时任务）返回 null。
     */
    private String resolveClientIp() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes)) {
                return null;
            }
            HttpServletRequest req = ((ServletRequestAttributes) attrs).getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                int comma = xff.indexOf(',');
                return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
