package com.company.knowledge.common.exception;

import lombok.Getter;

/**
 * 业务异常。携带业务码，用于 {@link GlobalExceptionHandler} 包装成 {@code Result.error}。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static BizException of(int code, String msg) {
        return new BizException(code, msg);
    }

    public static BizException of(int code, String msg, Throwable cause) {
        return new BizException(code, msg, cause);
    }
}
