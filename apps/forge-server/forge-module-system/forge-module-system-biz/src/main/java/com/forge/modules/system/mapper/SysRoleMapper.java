package com.forge.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.forge.modules.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * sys_user_role 不在租户忽略表内，认证上下文下由租户拦截器自动注入 tenant_id 条件
     */
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 根据角色编码查询角色ID（用于租户管理员初始化）
     */
    Long selectIdByCode(@Param("code") String code);
}
