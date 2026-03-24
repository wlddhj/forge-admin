package com.forge.admin.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.DataPermission;
import com.forge.admin.modules.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper
 *
 * @author standadmin
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 直接查询用户（不触发数据权限拦截器）
     * 用于 JWT 认证等场景，避免循环依赖
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    SysUser selectByUsernameSimple(@Param("username") String username);

    /**
     * 根据用户ID查询角色编码列表
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询权限编码列表
     */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询角色ID列表
     */
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询角色名称列表
     */
    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);

    /**
     * 分页查询用户列表（带数据权限过滤）
     *
     * @param page 分页参数
     * @param username 用户名（模糊查询）
     * @param nickname 昵称（模糊查询）
     * @param phone 手机号（模糊查询）
     * @param status 状态
     * @param deptId 部门ID
     * @return 用户分页列表
     */
    @DataPermission(userAlias = "u", deptAlias = "d")
    IPage<SysUser> selectUserPageWithPermission(
            Page<SysUser> page,
            @Param("username") String username,
            @Param("nickname") String nickname,
            @Param("phone") String phone,
            @Param("status") Integer status,
            @Param("deptId") Long deptId
    );

    /**
     * 查询用户列表（带数据权限过滤）用于导出
     *
     * @param username 用户名（模糊查询）
     * @param nickname 昵称（模糊查询）
     * @param phone 手机号（模糊查询）
     * @param status 状态
     * @param deptId 部门ID
     * @return 用户列表
     */
    @DataPermission(userAlias = "u", deptAlias = "d")
    List<SysUser> selectUserListWithPermission(
            @Param("username") String username,
            @Param("nickname") String nickname,
            @Param("phone") String phone,
            @Param("status") Integer status,
            @Param("deptId") Long deptId
    );
}
