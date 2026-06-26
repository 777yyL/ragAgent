package com.company.knowledge.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.permission.entity.PermissionPolicy;

/**
 * 权限策略 Mapper。基于 MyBatis-Plus {@link BaseMapper}，业务查询走 Wrapper。
 *
 * <p>注意：实体使用 {@code autoResultMap=true}，数组字段通过
 * {@code StringArrayTypeHandler} 自动处理，无需手工 result 写入。
 */
public interface PermissionPolicyMapper extends BaseMapper<PermissionPolicy> {
}
