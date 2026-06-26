package com.company.knowledge.permission.handler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link StringArrayTypeHandler} 单元测试。
 *
 * <p>仅覆盖静态 {@code toStringArray(Array)} 辅助方法（通过反射访问私有方法），
 * 不依赖 PG JDBC 驱动 / 数据库连接。
 */
class StringArrayTypeHandlerTest {

    /**
     * 自定义最小 Array 桩，避免依赖 PG JDBC 驱动。
     */
    private static java.sql.Array mockArray(final Object raw) {
        return new java.sql.Array() {
            @Override
            public String getBaseTypeName() {
                return "varchar";
            }

            @Override
            public int getBaseType() {
                return 0;
            }

            @Override
            public Object getArray() {
                return raw;
            }

            @Override
            public Object getArray(java.util.Map<String, Class<?>> map) {
                return raw;
            }

            @Override
            public Object getArray(long index, int count) {
                return raw;
            }

            @Override
            public Object getArray(long index, int count, java.util.Map<String, Class<?>> map) {
                return raw;
            }

            @Override
            public java.sql.ResultSet getResultSet() {
                return null;
            }

            @Override
            public java.sql.ResultSet getResultSet(java.util.Map<String, Class<?>> map) {
                return null;
            }

            @Override
            public java.sql.ResultSet getResultSet(long index, int count) {
                return null;
            }

            @Override
            public java.sql.ResultSet getResultSet(long index, int count, java.util.Map<String, Class<?>> map) {
                return null;
            }

            @Override
            public void free() {
            }
        };
    }

    private String[] callToStringArray(Object raw) throws Exception {
        Method m = StringArrayTypeHandler.class.getDeclaredMethod("toStringArray", java.sql.Array.class);
        m.setAccessible(true);
        return (String[]) m.invoke(null, mockArray(raw));
    }

    @Test
    void toStringArray_nullArray_returnsNull() throws Exception {
        Method m = StringArrayTypeHandler.class.getDeclaredMethod("toStringArray", java.sql.Array.class);
        m.setAccessible(true);
        // 直接传 null
        assertNull((String[]) m.invoke(null, (Object) null));
    }

    @Test
    void toStringArray_stringArray_returnsDirectly() throws Exception {
        String[] in = new String[]{"VIEW", "SEARCH"};
        String[] out = callToStringArray(in);
        assertArrayEquals(in, out);
    }

    @Test
    void toStringArray_objectArray_coercesToString() throws Exception {
        Object[] in = new Object[]{"VIEW", 42, Boolean.TRUE};
        String[] out = callToStringArray(in);
        assertEquals(3, out.length);
        assertEquals("VIEW", out[0]);
        assertEquals("42", out[1]);
        assertEquals("true", out[2]);
    }

    @Test
    void toStringArray_singleObject_wrapsAsSingleElement() throws Exception {
        String[] out = callToStringArray("solo");
        assertEquals(1, out.length);
        assertEquals("solo", out[0]);
    }
}
