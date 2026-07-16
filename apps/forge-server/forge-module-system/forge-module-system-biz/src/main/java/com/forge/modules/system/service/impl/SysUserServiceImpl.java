package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.framework.mybatis.annotation.DataPermission;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.CryptoUtils;
import com.forge.common.utils.UserContext;
import com.forge.modules.system.auth.util.PasswordValidator;
import com.forge.modules.system.dto.user.*;
import com.forge.modules.system.entity.SysDept;
import com.forge.modules.system.entity.SysRole;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.entity.SysUserPosition;
import com.forge.modules.system.entity.SysUserRole;
import com.forge.modules.system.mapper.SysRoleDeptMapper;
import com.forge.modules.system.mapper.SysUserMapper;
import com.forge.modules.system.mapper.SysUserPositionMapper;
import com.forge.modules.system.mapper.SysUserRoleMapper;
import com.forge.modules.system.service.SysDeptService;
import com.forge.modules.system.service.SysRoleService;
import com.forge.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserPositionMapper sysUserPositionMapper;
    private final PasswordEncoder passwordEncoder;
    private final SysDeptService sysDeptService;
    private final SysRoleService sysRoleService;
    private final SysRoleDeptMapper sysRoleDeptMapper;
    private final com.forge.modules.system.auth.util.PasswordValidator passwordValidator;
    private final com.forge.modules.system.auth.properties.PasswordPolicyProperties passwordPolicyProperties;
    private final com.forge.modules.system.service.SysUserPasswordHistoryService passwordHistoryService;
    private final CryptoUtils cryptoUtils;
    private final com.forge.modules.system.auth.service.LoginAttemptService loginAttemptService;

    @Override
    @DataPermission(enable = false) // 禁用数据权限，避免循环依赖
    public SysUser getByUsername(Long tenantId, String username) {
        log.info("[用户查询] 查询租户: {}, 用户名: '{}'", tenantId, username);
        // 使用原生 SQL 查询，避免 MyBatis-Plus 拦截器问题
        SysUser user = sysUserMapper.selectByUsernameSimple(tenantId, username);
        log.info("[用户查询] 查询结果: {}", user != null ? "找到用户: " + user.getUsername() + " (ID: " + user.getId() + ")" : "用户不存在");
        if (user != null) {
            // 加载用户角色并设置 deptIds
            loadUserRolesWithDataScope(user);
        }
        return user;
    }

    /**
     * 加载用户角色及其数据权限信息
     *
     * @param user 用户
     */
    private void loadUserRolesWithDataScope(SysUser user) {
        List<Long> roleIds = sysUserMapper.selectRoleIdsByUserId(user.getId());
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysRole> roles = sysRoleService.listByIds(roleIds);
            if (roles != null && !roles.isEmpty()) {
                // 为每个角色加载 deptIds
                for (SysRole role : roles) {
                    if ("2".equals(role.getDataScope())) {
                        List<Long> deptIds = sysRoleDeptMapper.selectDeptIdsByRoleId(role.getId());
                        role.setDeptIds(deptIds);
                    }
                }
                user.setRoles(roles);
                user.setRoleIds(roleIds);
            }
        }
    }

    @Override
    public Page<UserResponse> pageUsers(UserQueryRequest request) {
        Page<SysUser> page = new Page<>(request.getPageNum(), request.getPageSize());

        // email_suffix 存的是加盐 SHA-256 哈希，搜索时把明文转成同样的哈希再传给 mapper
        String emailHash = StrUtil.isNotBlank(request.getEmail())
                ? cryptoUtils.hashForSearch(request.getEmail()) : null;

        // 使用带数据权限过滤的查询方法
        Page<SysUser> userPage = (Page<SysUser>) sysUserMapper.selectUserPageWithPermission(
                page,
                request.getUsername(),
                request.getNickname(),
                request.getPhone(),
                emailHash,
                request.getStatus(),
                request.getDeptId()
        );

        Page<UserResponse> responsePage = new Page<>();
        responsePage.setCurrent(userPage.getCurrent());
        responsePage.setSize(userPage.getSize());
        responsePage.setTotal(userPage.getTotal());
        responsePage.setRecords(userPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public UserResponse getUserDetail(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        UserResponse response = convertToResponse(user);
        // 查询角色ID列表
        List<Long> roleIds = sysUserMapper.selectRoleIdsByUserId(id);
        response.setRoleIds(roleIds);
        // 查询岗位ID列表
        List<Long> positionIds = sysUserPositionMapper.selectPositionIdsByUserId(id);
        response.setPositionIds(positionIds);
        return response;
    }

@Override
    @Transactional(rollbackFor = Exception.class)
    public void addUser(UserRequest request) {
        // 检查用户名是否存在（同一租户下不能重复）
        Long tenantId = UserContext.get().getTenantId();
        if (tenantId == null) {
            throw new BusinessException("无法识别当前租户，请检查请求头 X-Tenant-Id");
        }
        if (getByUsername(tenantId, request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        SysUser user = new SysUser();
        BeanUtil.copyProperties(request, user, "password", "roleIds", "positionIds");
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        // 同步明文搜索字段（phone/email 字段本身会被 EncryptTypeHandler 加密）
        fillSearchSuffixes(user);
        // tenantId 由 MyBatis Plus TenantLineInnerInterceptor 从 TenantContextHolder 自动注入
        save(user);

        // 保存用户角色关联
        log.debug("新增用户角色关联 - 用户ID: {}, 角色IDs: {}", user.getId(), request.getRoleIds());
        saveUserRoles(user.getId(), request.getRoleIds());
        // 保存用户岗位关联
        saveUserPositions(user.getId(), request.getPositionIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserRequest request) {
        SysUser user = getById(request.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 跨租户防护：不允许跨租户修改用户（仅平台超管可跨租户）
        Long currentTenantId = UserContext.get().getTenantId();
        UserContext currentUser = UserContext.get();
        boolean isPlatformAdmin = currentUser != null && currentUser.isPlatformAdmin();
        if (currentTenantId != null && !isPlatformAdmin
                && !currentTenantId.equals(user.getTenantId())) {
            throw new BusinessException("无权修改其他租户的用户");
        }

        // 检查用户名是否重复（同一租户下不能重复）
        if (!user.getUsername().equals(request.getUsername())) {
            if (getByUsername(user.getTenantId(), request.getUsername()) != null) {
                throw new BusinessException(ResultCode.USER_EXISTS);
            }
        }

        BeanUtil.copyProperties(request, user, "password", "roleIds", "positionIds");
        if (StrUtil.isNotBlank(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        // 同步明文搜索字段
        fillSearchSuffixes(user);
        // 保留原 tenantId，防止通过 updateUser 迁移用户到其他租户
        user.setTenantId(user.getTenantId());
        updateById(user);

        // 更新用户角色关联
        sysUserRoleMapper.deleteByUserId(user.getId());
        saveUserRoles(user.getId(), request.getRoleIds());
        // 更新用户岗位关联
        sysUserPositionMapper.deleteByUserId(user.getId());
        saveUserPositions(user.getId(), request.getPositionIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        removeById(id);
        sysUserRoleMapper.deleteByUserId(id);
        sysUserPositionMapper.deleteByUserId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUsers(List<Long> ids) {
        removeByIds(ids);
        ids.forEach(id -> {
            sysUserRoleMapper.deleteByUserId(id);
            sysUserPositionMapper.deleteByUserId(id);
        });
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setStatus(status);
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 生成符合复杂度的随机密码，强制下次登录修改
        String randomPassword = cryptoUtils.generateRandomPassword(passwordPolicyProperties.getRandomPasswordLength());
        String encodedPassword = passwordEncoder.encode(randomPassword);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // 仅更新密码相关字段，避免覆盖并发修改，并绕过 phone/email 字段 EncryptTypeHandler 重加密
        update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, encodedPassword)
                .set(SysUser::getPasswordUpdateTime, now)
                .set(SysUser::getFirstLogin, 1)
                .set(SysUser::getPasswordErrorCount, 0)
                .set(SysUser::getLockTime, null));
        // 重置后清除历史，避免新密码被旧历史拦截
        passwordHistoryService.remove(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.forge.modules.system.entity.SysUserPasswordHistory>()
                .eq(com.forge.modules.system.entity.SysUserPasswordHistory::getUserId, id));
        return randomPassword;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 清除数据库锁定状态
        user.setPasswordErrorCount(0);
        user.setLockTime(null);
        updateById(user);
        // 清除 Redis 锁定标记和失败计数
        loginAttemptService.unlock(user.getUsername());
        log.info("管理员解锁用户: id={}, username={}", id, user.getUsername());
    }

    @Override
    public List<SimpleGrantedAuthority> getUserPermissions(Long userId) {
        List<String> permissions = sysUserMapper.selectPermissionCodesByUserId(userId);
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserRoleCodes(Long userId) {
        return sysUserMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public List<String> getUserPermissionCodes(Long userId) {
        return sysUserMapper.selectPermissionCodesByUserId(userId);
    }

    /**
     * 同步可搜索的 suffix 字段：
     * - phone_suffix：取明文手机号后 4 位（精确查询用）
     * - email_suffix：明文邮箱经 SHA-256 + AES_KEY 盐哈希后存储（即使拖库也无法还原）
     *
     * 由于 phone/email 字段在 SysUser 上有 @EncryptField 注解，写入时会自动加密。
     * suffix 字段在 DB 层支持 phone_suffix 精确 / email_suffix 精确匹配，
     * 服务端在 service 层把明文输入转成同样的哈希再传给 mapper。
     */
    private void fillSearchSuffixes(SysUser user) {
        String encryptedPrefix = "ENCv1:";
        String phone = user.getPhone();
        if (StrUtil.isNotBlank(phone) && !phone.startsWith(encryptedPrefix)) {
            String digits = phone.replaceAll("\\D", "");
            user.setPhoneSuffix(digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits);
        } else if (StrUtil.isBlank(phone)) {
            user.setPhoneSuffix(null);
        }
        String email = user.getEmail();
        if (StrUtil.isNotBlank(email) && !email.startsWith(encryptedPrefix)) {
            user.setEmailSuffix(cryptoUtils.hashForSearch(email));
        } else if (StrUtil.isBlank(email)) {
            user.setEmailSuffix(null);
        }
    }

    private UserResponse convertToResponse(SysUser user) {
        UserResponse response = new UserResponse();
        BeanUtil.copyProperties(user, response);
        // 查询并设置部门名称
        if (user.getDeptId() != null) {
            SysDept dept = sysDeptService.getById(user.getDeptId());
            if (dept != null) {
                response.setDeptName(dept.getDeptName());
            }
        }
        // 查询并设置角色名称
        List<String> roleNames = sysUserMapper.selectRoleNamesByUserId(user.getId());
        response.setRoleNames(roleNames);
        // 查询并设置岗位名称
        List<String> positionNames = sysUserPositionMapper.selectPositionNamesByUserId(user.getId());
        response.setPositionNames(positionNames);
        return response;
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysUserRole> userRoles = roleIds.stream()
                    .map(roleId -> {
                        SysUserRole userRole = new SysUserRole();
                        userRole.setUserId(userId);
                        userRole.setRoleId(roleId);
                        return userRole;
                    })
                    .collect(Collectors.toList());
            sysUserRoleMapper.batchInsert(userRoles);
        }
    }

    private void saveUserPositions(Long userId, List<Long> positionIds) {
        if (positionIds != null && !positionIds.isEmpty()) {
            List<SysUserPosition> userPositions = positionIds.stream()
                    .map(positionId -> {
                        SysUserPosition userPosition = new SysUserPosition();
                        userPosition.setUserId(userId);
                        userPosition.setPositionId(positionId);
                        return userPosition;
                    })
                    .collect(Collectors.toList());
            sysUserPositionMapper.batchInsert(userPositions);
        }
    }

    @Override
    public SysUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        Long tenantId = UserContext.get().getTenantId();
        return getByUsername(tenantId, username);
    }

    @Override
    public void updateProfile(UserProfileRequest request) {
        SysUser user = getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (StrUtil.isNotBlank(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StrUtil.isNotBlank(request.getPhone())) {
            user.setPhone(request.getPhone());
        }
        if (StrUtil.isNotBlank(request.getEmail())) {
            user.setEmail(request.getEmail());
        }
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(UserPasswordRequest request) {
        SysUser user = getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 1. 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        // 2. 新旧密码不能相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        // 3. 复杂度校验
        PasswordValidator.Result vr = passwordValidator.validate(request.getNewPassword());
        if (!vr.isSuccess()) {
            throw new BusinessException(vr.getMessage());
        }
        // 4. 历史密码校验（最近 N 次不复用）
        if (passwordHistoryService.isPasswordInHistory(
                user.getId(), request.getNewPassword(), passwordPolicyProperties.getHistorySize())) {
            throw new BusinessException("新密码不能与最近 " + passwordPolicyProperties.getHistorySize() + " 次使用过的密码相同");
        }
        // 5. 编码并持久化新密码
        String newHash = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(newHash);
        user.setPasswordUpdateTime(java.time.LocalDateTime.now());
        user.setFirstLogin(0);
        updateById(user);
        // 6. 保存到历史表
        passwordHistoryService.saveAndTrim(user.getId(), newHash, passwordPolicyProperties.getHistorySize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void firstLoginChangePassword(Long tenantId, com.forge.modules.system.auth.dto.FirstLoginChangePasswordRequest request) {
        // 仅 firstLogin=1 的用户允许调用此接口，避免被普通用户滥用
        SysUser user = getByUsername(tenantId, request.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getFirstLogin() == null || user.getFirstLogin() != 1) {
            throw new BusinessException("该用户无需强制改密，请使用个人中心修改密码");
        }
        // 1. 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        // 2. 新旧密码不能相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        // 3. 复杂度校验
        PasswordValidator.Result vr = passwordValidator.validate(request.getNewPassword());
        if (!vr.isSuccess()) {
            throw new BusinessException(vr.getMessage());
        }
        // 4. 历史密码校验
        if (passwordHistoryService.isPasswordInHistory(
                user.getId(), request.getNewPassword(), passwordPolicyProperties.getHistorySize())) {
            throw new BusinessException("新密码不能与最近 " + passwordPolicyProperties.getHistorySize() + " 次使用过的密码相同");
        }
        // 5. 编码并持久化新密码
        String newHash = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(newHash);
        user.setPasswordUpdateTime(java.time.LocalDateTime.now());
        user.setFirstLogin(0);
        updateById(user);
        // 6. 保存到历史表
        passwordHistoryService.saveAndTrim(user.getId(), newHash, passwordPolicyProperties.getHistorySize());
        log.info("[首次登录强制改密] 用户 {} 已修改密码", user.getUsername());
    }

    @Override
    public void updateAvatar(UserAvatarRequest request) {
        SysUser user = getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        user.setAvatar(request.getAvatar());
        updateById(user);
    }

    @Override
    public List<UserExport> getExportList(UserQueryRequest request) {
        String emailHash = StrUtil.isNotBlank(request.getEmail())
                ? cryptoUtils.hashForSearch(request.getEmail()) : null;
        // 使用带数据权限过滤的查询方法
        List<SysUser> users = sysUserMapper.selectUserListWithPermission(
                request.getUsername(),
                request.getNickname(),
                request.getPhone(),
                emailHash,
                request.getStatus(),
                request.getDeptId()
        );

        return users.stream().map(user -> {
            UserExport export = new UserExport();
            export.setUsername(user.getUsername());
            export.setNickname(user.getNickname());
            export.setPhone(user.getPhone());
            export.setEmail(user.getEmail());
            export.setStatus(user.getStatus() == 1 ? "启用" : "禁用");
            export.setCreateTime(user.getCreateTime());
            // 获取部门名称
            if (user.getDeptId() != null) {
                SysDept dept = sysDeptService.getById(user.getDeptId());
                if (dept != null) {
                    export.setDeptName(dept.getDeptName());
                }
            }
            return export;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResultDTO importUsers(List<UserImportDTO> importUsers, boolean updateSupport) {
        if (CollUtil.isEmpty(importUsers)) {
            throw new BusinessException("导入数据不能为空");
        }

        UserImportResultDTO result = UserImportResultDTO.builder().build();

        for (UserImportDTO importUser : importUsers) {
            try {
                // 校验必填字段
                if (StrUtil.isBlank(importUser.getUsername())) {
                    result.getFailureUsernames().put("", "用户名不能为空");
                    continue;
                }
                if (StrUtil.isBlank(importUser.getNickname())) {
                    result.getFailureUsernames().put(importUser.getUsername(), "昵称不能为空");
                    continue;
                }

                // 判断用户名是否已存在（同一租户下不能重复）
                SysUser existUser = sysUserMapper.selectByUsernameSimple(UserContext.get().getTenantId(), importUser.getUsername());

                if (existUser == null) {
                    // 新增用户
                    SysUser user = new SysUser();
                    BeanUtil.copyProperties(importUser, user);
                    String defaultPassword = cryptoUtils.generateRandomPassword(passwordPolicyProperties.getRandomPasswordLength());
                    user.setPassword(passwordEncoder.encode(defaultPassword));
                    user.setPasswordUpdateTime(java.time.LocalDateTime.now());
                    user.setFirstLogin(1);
                    if (user.getStatus() == null) {
                        user.setStatus(1);
                    }
                    save(user);
                    result.getCreateUsernames().add(importUser.getUsername());
                } else if (updateSupport) {
                    // 更新已有用户
                    BeanUtil.copyProperties(importUser, existUser, "id", "password");
                    updateById(existUser);
                    result.getUpdateUsernames().add(importUser.getUsername());
                } else {
                    result.getFailureUsernames().put(importUser.getUsername(), "用户名已存在");
                }
            } catch (Exception e) {
                result.getFailureUsernames().put(importUser.getUsername(), e.getMessage());
            }
        }

        return result;
    }

    @Override
    public List<UserSimpleResponse> getAllUsersSimple() {
        return lambdaQuery().eq(SysUser::getStatus, 1)
                .select(SysUser::getId, SysUser::getNickname)
                .list().stream().map(user -> {
                    UserSimpleResponse resp = new UserSimpleResponse();
                    resp.setId(user.getId());
                    resp.setNickname(user.getNickname());
                    return resp;
                }).collect(Collectors.toList());
    }
}
