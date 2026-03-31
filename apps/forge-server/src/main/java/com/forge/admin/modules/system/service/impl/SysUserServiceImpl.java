package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.annotation.DataPermission;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.common.utils.UserContext;
import com.forge.admin.modules.system.dto.user.*;
import com.forge.admin.modules.system.entity.SysDept;
import com.forge.admin.modules.system.entity.SysRole;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.entity.SysUserRole;
import com.forge.admin.modules.system.mapper.SysRoleDeptMapper;
import com.forge.admin.modules.system.mapper.SysUserMapper;
import com.forge.admin.modules.system.mapper.SysUserRoleMapper;
import com.forge.admin.modules.system.service.SysDeptService;
import com.forge.admin.modules.system.service.SysRoleService;
import com.forge.admin.modules.system.service.SysUserService;
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
    private final PasswordEncoder passwordEncoder;
    private final SysDeptService sysDeptService;
    private final SysRoleService sysRoleService;
    private final SysRoleDeptMapper sysRoleDeptMapper;

    @Override
    @DataPermission(enable = false) // 禁用数据权限，避免循环依赖
    public SysUser getByUsername(String username) {
        log.info("[用户查询] 查询用户名: '{}', 长度: {}", username, username != null ? username.length() : 0);
        // 使用原生 SQL 查询，避免 MyBatis-Plus 拦截器问题
        SysUser user = sysUserMapper.selectByUsernameSimple(username);
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

        // 使用带数据权限过滤的查询方法
        Page<SysUser> userPage = (Page<SysUser>) sysUserMapper.selectUserPageWithPermission(
                page,
                request.getUsername(),
                request.getNickname(),
                request.getPhone(),
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
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUser(UserRequest request) {
        // 检查用户名是否存在
        if (getByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        SysUser user = new SysUser();
        BeanUtil.copyProperties(request, user, "password", "roleIds");
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        save(user);

        // 保存用户角色关联
        log.debug("新增用户角色关联 - 用户ID: {}, 角色IDs: {}", user.getId(), request.getRoleIds());
        saveUserRoles(user.getId(), request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserRequest request) {
        SysUser user = getById(request.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查用户名是否重复
        if (!user.getUsername().equals(request.getUsername())) {
            if (getByUsername(request.getUsername()) != null) {
                throw new BusinessException(ResultCode.USER_EXISTS);
            }
        }

        BeanUtil.copyProperties(request, user, "password", "roleIds");
        if (StrUtil.isNotBlank(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        updateById(user);

        // 更新用户角色关联
        sysUserRoleMapper.deleteByUserId(user.getId());
        saveUserRoles(user.getId(), request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        removeById(id);
        sysUserRoleMapper.deleteByUserId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUsers(List<Long> ids) {
        removeByIds(ids);
        ids.forEach(sysUserRoleMapper::deleteByUserId);
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
    public void resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setPassword(passwordEncoder.encode("123456"));
        updateById(user);
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

    @Override
    public SysUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        return getByUsername(username);
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
    public void updatePassword(UserPasswordRequest request) {
        SysUser user = getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
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
        // 使用带数据权限过滤的查询方法
        List<SysUser> users = sysUserMapper.selectUserListWithPermission(
                request.getUsername(),
                request.getNickname(),
                request.getPhone(),
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
}
