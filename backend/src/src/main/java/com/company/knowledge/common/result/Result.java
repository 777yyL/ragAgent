package com.company.knowledge.common.result;

import lombok.Data;

/**
 * 统一响应包装。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    /** 业务码：0 成功，非 0 失败 */
    private int code;

    /** 提示信息（失败时必填） */
    private String msg;

    /** 业务数据 */
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
