package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.user.*;
import com.forge.admin.modules.system.entity.SysUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author standadmin
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 根据用户名查询用户
     */
    SysUser getByUsername(String username);

    /**
     * 分页查询用户
     */
    Page<UserResponse> pageUsers(UserQueryRequest request);

    /**
     * 获取用户详情
     */
    UserResponse getUserDetail(Long id);

    /**
     * 新增用户
     */
    void addUser(UserRequest request);

    /**
     * 更新用户
     */
    void updateUser(UserRequest request);

    /**
     * 删除用户
     */
    void deleteUser(Long id);

    /**
     * 批量删除用户
     */
    void deleteUsers(List<Long> ids);

    /**
     * 更新用户状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 重置密码
     */
    void resetPassword(Long id);

    /**
     * 获取用户权限
     */
    List<SimpleGrantedAuthority> getUserPermissions(Long userId);

    /**
     * 获取用户角色编码列表
     */
    List<String> getUserRoleCodes(Long userId);

    /**
     * 获取用户权限编码列表
     */
    List<String> getUserPermissionCodes(Long userId);

    /**
     * 获取当前登录用户
     */
    SysUser getCurrentUser();

    /**
     * 更新个人资料
     */
    void updateProfile(UserProfileRequest request);

    /**
     * 修改密码
     */
    void updatePassword(UserPasswordRequest request);

    /**
     * 更新头像
     */
    void updateAvatar(UserAvatarRequest request);

    /**
     * 获取用户导出列表
     */
    List<UserExport> getExportList(UserQueryRequest request);
}
