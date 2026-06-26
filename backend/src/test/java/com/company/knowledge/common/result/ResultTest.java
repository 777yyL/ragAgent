package com.company.knowledge.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 响应包装类的单元测试。
 */
class ResultTest {

    @Test
    void success_withData_shouldReturnCodeZeroAndData() {
        Result<String> result = Result.success("hello");
        assertEquals(0, result.getCode());
        assertEquals("hello", result.getData());
        assertNull(result.getMsg());
    }

    @Test
    void success_noData_shouldReturnCodeZeroAndNullData() {
        Result<Void> result = Result.success();
        assertEquals(0, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void error_shouldReturnNonZeroCodeAndMessage() {
        Result<?> result = Result.error(1001, "invalid param");
        assertEquals(1001, result.getCode());
        assertEquals("invalid param", result.getMsg());
        assertNull(result.getData());
    }
}
