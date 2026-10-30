package com.forge.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户角色关联 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 删除用户角色关联
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除角色的全部用户关联
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色已关联的用户ID列表
     */
    java.util.List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入用户角色
     */
    int batchInsert(@Param("list") java.util.List<SysUserRole> list);
}
