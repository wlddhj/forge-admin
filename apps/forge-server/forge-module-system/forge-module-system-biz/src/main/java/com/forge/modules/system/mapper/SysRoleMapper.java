package com.forge.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户ID查询角色列表
     */
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 根据菜单ID查询角色ID列表
     */
    List<Long> selectRoleIdsByMenuId(@Param("menuId") Long menuId);

    /**
     * 根据角色编码查询角色ID（用于租户管理员初始化）
     */
    @Select("SELECT id FROM sys_role WHERE role_code = #{code} AND deleted = 0 LIMIT 1")
    Long selectIdByCode(@Param("code") String code);
}
