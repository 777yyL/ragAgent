package com.company.knowledge.common.exception;

import com.company.knowledge.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>dev 模式下会暴露异常类名 + 消息（方便本地调试），prod 模式只返回 "internal error"。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    @ExceptionHandler(BizException.class)
    public Result<?> handleBiz(BizException e) {
        log.warn("biz error: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("validation failed: {}", msg);
        return Result.error(4001, msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("type mismatch: param={}, value={}, required={}", e.getName(), e.getValue(), e.getRequiredType());
        return Result.error(400, "参数 " + e.getName() + " 格式错误: 期望 " +
                (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "?") +
                "，实际值: " + e.getValue());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleUnknown(HttpServletRequest req, Exception e) {
        log.error("unexpected error on {}: {}", req.getRequestURI(), e.getMessage(), e);
        if ("dev".equals(activeProfile)) {
            // dev 模式暴露详细错误（方便调试）
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (e.getCause() != null) {
                detail += " | caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage();
            }
            return Result.error(500, detail);
        }
        return Result.error(500, "internal error");
    }
}
