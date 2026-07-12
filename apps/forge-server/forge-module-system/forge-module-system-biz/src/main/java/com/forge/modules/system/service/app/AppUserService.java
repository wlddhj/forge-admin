package com.forge.modules.system.service.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.system.dto.app.AppUserDetailResponse;
import com.forge.modules.system.dto.app.AppUserProfileResponse;
import com.forge.modules.system.dto.app.AppUserProfileUpdateRequest;
import com.forge.modules.system.dto.app.AppUserQueryRequest;
import com.forge.modules.system.entity.AppUser;

public interface AppUserService {

    AppUser getByOpenId(String openId);

    /**
     * 按 (tenantId, openId) 查询小程序用户
     *
     * @param tenantId 租户ID（必传）
     * @param openId 微信 openId
     */
    AppUser getByTenantIdAndOpenId(Long tenantId, String openId);

    AppUser getById(Long id);

    AppUser createAppUser(AppUser appUser);

    AppUserProfileResponse getProfile(Long userId);

    void updateProfile(Long userId, AppUserProfileUpdateRequest request);

    void updateLastLoginTime(Long userId);

    /**
     * 绑定/换绑手机号
     * @param userId 用户ID
     * @param phone 新手机号
     */
    void bindPhone(Long userId, String phone);

    /**
     * 注销账号（软删除）+ openid置空
     * @param userId 用户ID
     */
    void deactivate(Long userId);

    /**
     * 更新状态（封禁/解封），含强制下线
     * @param userId 用户ID
     * @param status 状态：0-正常 1-封禁
     */
    void updateStatus(Long userId, Integer status);

    /**
     * 删除用户token（强制下线）
     * @param userId 用户ID
     */
    void clearUserSessions(Long userId);

    /**
     * 后台分页查询
     * @param request 查询参数
     * @return 分页结果
     */
    IPage<AppUserDetailResponse> adminPage(AppUserQueryRequest request);

    /**
     * 后台详情
     * @param id 用户ID
     * @return 详情
     */
    AppUserDetailResponse adminDetail(Long id);

    /**
     * 后台重置资料
     * @param id 用户ID
     * @param nickname 昵称
     * @param avatar 头像
     */
    void adminResetProfile(Long id, String nickname, String avatar);
}
