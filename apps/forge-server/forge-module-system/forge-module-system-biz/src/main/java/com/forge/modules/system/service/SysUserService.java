package com.forge.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.forge.modules.system.dto.user.*;
import com.forge.modules.system.entity.SysUser;
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
    SysUser getByUsername(Long tenantId, String username);

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
     * 重置密码（返回随机生成的明文密码，仅本次展示）
     */
    String resetPassword(Long id);

    /**
     * 解锁用户（清除登录失败计数和锁定标记）
     */
    void unlock(Long id);

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
     * 首次登录强制改密（不走 token 鉴权，使用用户名 + 旧密码鉴权）。
     * 仅当用户的 firstLogin=1 时允许调用，改密成功后清除该标记。
     *
     * @param tenantId 租户 ID（多租户模式由 Controller 从 request.tenantCode 解析；单租户模式传 1）
     * @param request  请求体（username / oldPassword / newPassword / tenantCode）
     */
    void firstLoginChangePassword(Long tenantId, com.forge.modules.system.auth.dto.FirstLoginChangePasswordRequest request);

    /**
     * 更新头像
     */
    void updateAvatar(UserAvatarRequest request);

    /**
     * 获取用户导出列表
     */
    List<UserExport> getExportList(UserQueryRequest request);

    /**
     * 导入用户
     *
     * @param importUsers    导入的用户数据列表
     * @param updateSupport  是否更新已存在的用户
     * @return 导入结果
     */
    UserImportResultDTO importUsers(List<UserImportDTO> importUsers, boolean updateSupport);

    /**
     * 获取所有用户简单列表（下拉选择用）
     */
    List<UserSimpleResponse> getAllUsersSimple();
}
