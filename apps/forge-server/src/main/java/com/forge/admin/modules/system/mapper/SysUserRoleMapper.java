package com.forge.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.admin.modules.system.entity.SysUserRole;
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
     * 批量插入用户角色
     */
    int batchInsert(@Param("list") java.util.List<SysUserRole> list);
}
