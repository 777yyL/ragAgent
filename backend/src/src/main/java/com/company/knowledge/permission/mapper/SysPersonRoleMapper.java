package com.company.knowledge.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.permission.entity.SysPersonRole;
import org.apache.ibatis.annotations.Param;

/**
 * 人员-角色关系 Mapper。
 */
public interface SysPersonRoleMapper extends BaseMapper<SysPersonRole> {

    /**
     * 按 personId 物理删除全部关系（{@code RoleService.assignRoles} 先删后插）。
     *
     * @param personId 海康 personId
     * @return 删除行数
     */
    int deleteByPersonId(@Param("personId") String personId);
}
