package com.company.knowledge.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.knowledge.permission.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper。基于 MyBatis-Plus {@link BaseMapper}，多表 join 走自定义 SQL。
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 按 personId 查询其所有角色完整记录（join {@code sys_person_role}）。
     *
     * @param personId 海康 personId
     * @return 角色记录列表，可能为空
     */
    @Select("SELECT r.* FROM sys_role r "
            + "INNER JOIN sys_person_role pr ON r.role_id = pr.role_id "
            + "WHERE pr.person_id = #{personId}")
    List<SysRole> selectByPersonId(@Param("personId") String personId);
}
