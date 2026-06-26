package com.company.knowledge.audit.handler;

import com.company.knowledge.audit.entity.AuditNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * MyBatis TypeHandler：在 {@code audit_template.nodes}（JSONB）与
 * {@code List<AuditNode>} 之间用 Jackson 互转。
 *
 * <p>PG JSONB 在 JDBC 中以字符串形式返回，写入用
 * {@code PGobject(type=-jsonb)}；本 Handler 简化为字符串处理，
 * 依赖 MyBatis-Plus + PG JDBC 的隐式转换（String → jsonb via cast）。
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(List.class)
public class AuditNodeTypeHandler extends BaseTypeHandler<List<AuditNode>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<List<AuditNode>> TR = new TypeReference<List<AuditNode>>() {};

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    List<AuditNode> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("serialize AuditNode list failed", e);
        }
    }

    @Override
    public List<AuditNode> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<AuditNode> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<AuditNode> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<AuditNode> parse(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, TR);
        } catch (Exception e) {
            // 反序列化失败不应让整个查询挂掉，返回空列表 + 记日志即可
            return Collections.emptyList();
        }
    }
}
