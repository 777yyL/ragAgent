package com.company.knowledge.permission.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * PostgreSQL {@code VARCHAR[]} 与 Java {@code String[]} 互转 TypeHandler。
 *
 * <p>MyBatis-Plus 默认不支持 PG 数组，需手工注册。
 * 使用方式（实体字段）：
 *
 * <pre>{@code
 * @TableField(typeHandler = StringArrayTypeHandler.class)
 * private String[] actions;
 * }</pre>
 *
 * <p>读取：调用 {@link ResultSet#getArray(String)}，再 {@code getArray()} 拿到
 * {@code String[]}；写入：{@link Connection#createArrayOf(String, Object)}。
 */
@MappedJdbcTypes(JdbcType.ARRAY)
@MappedTypes(String[].class)
public class StringArrayTypeHandler extends BaseTypeHandler<String[]> {

    private static final String PG_TYPE = "varchar";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    String[] parameter, JdbcType jdbcType) throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf(PG_TYPE, parameter);
        ps.setArray(i, array);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toStringArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toStringArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toStringArray(cs.getArray(columnIndex));
    }

    /**
     * PG Array → Java {@code String[]}。
     *
     * <p>PG JDBC 驱动返回的实际类型为 {@code String[]}（varchar 类型）或
     * {@code Object[]}（其他类型），此处统一用 {@code toString} 兜底。
     */
    private static String[] toStringArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object raw = array.getArray();
        if (raw == null) {
            return null;
        }
        if (raw instanceof String[]) {
            return (String[]) raw;
        }
        if (raw instanceof Object[]) {
            Object[] arr = (Object[]) raw;
            String[] out = new String[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i] == null ? null : arr[i].toString();
            }
            return out;
        }
        // 不应发生：单值包装成单元素数组
        return new String[]{raw.toString()};
    }
}
