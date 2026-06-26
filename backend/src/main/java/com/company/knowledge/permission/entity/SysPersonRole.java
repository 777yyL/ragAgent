package com.company.knowledge.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 人员-角色关系。对应表 {@code sys_person_role}。
 *
 * <p>复合关系：{@code (person_id, role_id)} 唯一。
 * 由 {@code RoleService.assignRoles} 负责「先删后插」。
 */
@Data
@TableName("sys_person_role")
public class SysPersonRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 海康 personId */
    private String personId;

    /** 对应 {@link SysRole#getRoleId()} */
    private Long roleId;
}
