package com.forge.modules.system.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.modules.system.dto.app.AppUserDetailResponse;
import com.forge.modules.system.dto.app.AppUserProfileResponse;
import com.forge.modules.system.dto.app.AppUserProfileUpdateRequest;
import com.forge.modules.system.dto.app.AppUserQueryRequest;
import com.forge.modules.system.entity.AppUser;
import com.forge.modules.system.mapper.AppUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {

    private final AppUserMapper appUserMapper;
    private final StringRedisTemplate redis;

    @Override
    public AppUser getByOpenId(String openId) {
        return appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>().eq(AppUser::getOpenId, openId));
    }

    @Override
    public AppUser getById(Long id) {
        return appUserMapper.selectById(id);
    }

    @Override
    public AppUser createAppUser(AppUser appUser) {
        appUserMapper.insert(appUser);
        return appUser;
    }

    @Override
    public AppUserProfileResponse getProfile(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return AppUserProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .build();
    }

    @Override
    public void updateProfile(Long userId, AppUserProfileUpdateRequest request) {
        // 用户存在性校验
        AppUser existing = appUserMapper.selectById(userId);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        AppUser user = new AppUser();
        user.setId(userId);
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        appUserMapper.updateById(user);
    }

    @Override
    public void updateLastLoginTime(Long userId) {
        AppUser user = new AppUser();
        user.setId(userId);
        user.setLastLoginTime(LocalDateTime.now());
        appUserMapper.updateById(user);
    }

    @Override
    public void bindPhone(Long userId, String phone) {
        // 分布式锁防并发
        String lockKey = "app:lock:bind-phone:" + phone;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, lockValue, 5, TimeUnit.SECONDS);
        if (!locked) {
            throw new BusinessException(429, "操作繁忙，请稍后再试");
        }
        try {
            // 二次校验：phone 未被其他有效用户占用（排除当前 userId）
            LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                    .eq(AppUser::getPhone, phone)
                    .eq(AppUser::getDeleted, 0)
                    .ne(AppUser::getId, userId);
            if (appUserMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ResultCode.PHONE_ALREADY_BOUND);
            }
            // 更新
            AppUser update = new AppUser();
            update.setId(userId);
            update.setPhone(phone);
            update.setPhoneVerified(1);
            appUserMapper.updateById(update);
        } finally {
            // 验证锁值后再删除，防止误删其他线程的锁
            if (lockValue.equals(redis.opsForValue().get(lockKey))) {
                redis.delete(lockKey);
            }
        }
    }

    @Override
    public void deactivate(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 改写 openId：加 "#del_" + 时间戳后缀，截断防超长
        String newOpenId = user.getOpenId() + "#del_" + System.currentTimeMillis() / 1000;
        // 截断防止超长（open_id 字段为 VARCHAR(64)）
        if (newOpenId.length() > 64) {
            newOpenId = newOpenId.substring(0, 64);
            log.warn("openId 改写后超长，已截断: userId={}", userId);
        }
        AppUser update = new AppUser();
        update.setId(userId);
        update.setDeleted(1);
        update.setOpenId(newOpenId);
        update.setDeactivatedTime(LocalDateTime.now());
        appUserMapper.updateById(update);

        // 清 Redis sessions
        clearUserSessions(userId);
    }

    @Override
    public void updateStatus(Long userId, Integer status) {
        AppUser update = new AppUser();
        update.setId(userId);
        update.setStatus(status);
        appUserMapper.updateById(update);

        if (status == 1) {
            // 封禁时强制下线
            clearUserSessions(userId);
        }
    }

    @Override
    public void clearUserSessions(Long userId) {
        String sessionSetKey = "app_user_sessions:" + userId;
        Set<String> members = redis.opsForSet().members(sessionSetKey);
        if (members != null) {
            for (String token : members) {
                try {
                    if (token.startsWith("tok_")) {
                        // tok_ 前缀的是 tokenId，删除 app_session:{tokenId}
                        redis.delete("app_session:" + token);
                    } else {
                        // refreshToken，删除 app_refresh_token:{refreshToken}
                        redis.delete("app_refresh_token:" + token);
                    }
                } catch (Exception e) {
                    log.warn("删除 token 失败: {}", token, e);
                }
            }
        }
        redis.delete(sessionSetKey);
    }

    @Override
    public IPage<AppUserDetailResponse> adminPage(AppUserQueryRequest request) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getNickname()), AppUser::getNickname, request.getNickname());
        wrapper.like(StringUtils.hasText(request.getPhone()), AppUser::getPhone, request.getPhone());
        wrapper.like(StringUtils.hasText(request.getOpenId()), AppUser::getOpenId, request.getOpenId());
        wrapper.eq(request.getStatus() != null, AppUser::getStatus, request.getStatus());
        wrapper.ge(request.getCreateTimeStart() != null, AppUser::getCreateTime, request.getCreateTimeStart());
        wrapper.le(request.getCreateTimeEnd() != null, AppUser::getCreateTime, request.getCreateTimeEnd());
        wrapper.orderByDesc(AppUser::getCreateTime);

        Page<AppUser> page = appUserMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

        return page.convert(this::toDetailResponse);
    }

    @Override
    public AppUserDetailResponse adminDetail(Long id) {
        AppUser user = appUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toDetailResponse(user);
    }

    @Override
    public void adminResetProfile(Long id, String nickname, String avatar) {
        // 用户存在性校验
        AppUser existing = appUserMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        AppUser update = new AppUser();
        update.setId(id);
        if (StringUtils.hasText(nickname)) {
            update.setNickname(nickname);
        }
        if (StringUtils.hasText(avatar)) {
            update.setAvatar(avatar);
        }
        appUserMapper.updateById(update);
    }

    private AppUserDetailResponse toDetailResponse(AppUser user) {
        return AppUserDetailResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(desensitizePhone(user.getPhone()))
                .phoneVerified(user.getPhoneVerified())
                .openId(desensitizeOpenId(user.getOpenId()))
                .unionId(desensitizeOpenId(user.getUnionId()))
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .deactivatedTime(user.getDeactivatedTime())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    private String desensitizePhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String desensitizeOpenId(String openId) {
        if (openId == null || openId.length() < 8) {
            return openId;
        }
        return openId.substring(0, 4) + "****" + openId.substring(openId.length() - 4);
    }
}