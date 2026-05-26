package com.forge.admin.modules.workflow.identity;

import com.forge.admin.modules.system.entity.SysRole;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysRoleMapper;
import com.forge.admin.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.IdentityService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Flowable 身份桥接服务
 * 将 sys_user/sys_role 映射到 Flowable 的认证上下文
 */
@Service
@RequiredArgsConstructor
public class FlowableIdentityService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final IdentityService identityService;

    /**
     * 设置当前认证用户到 Flowable 上下文
     */
    public void setAuthenticatedUserId(Long userId) {
        identityService.setAuthenticatedUserId(String.valueOf(userId));
    }

    /**
     * 清除 Flowable 认证用户上下文
     */
    public void clearAuthenticatedUserId() {
        identityService.setAuthenticatedUserId(null);
    }

    /**
     * 根据用户ID获取用户名（昵称）
     */
    public String getUserName(Long userId) {
        if (userId == null) return "未知用户";
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getNickname() : "未知用户";
    }

    /**
     * 根据用户ID获取用户名（优先昵称，其次用户名）
     */
    public String getUserName(String userId) {
        if (userId == null || userId.isEmpty()) return "未知用户";
        try {
            return getUserName(Long.parseLong(userId));
        } catch (NumberFormatException e) {
            return "未知用户";
        }
    }

    /**
     * 根据角色ID获取角色名称
     */
    public String getGroupName(Long roleId) {
        if (roleId == null) return "未知角色";
        SysRole role = sysRoleMapper.selectById(roleId);
        return role != null ? role.getRoleName() : "未知角色";
    }

    /**
     * 批量获取用户名称
     */
    public Map<Long, String> getUserNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyMap();
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));
    }

    /**
     * 获取候选组名称列表
     */
    public List<String> getCandidateGroupNames(List<String> groupIds) {
        List<String> names = new ArrayList<>();
        for (String groupId : groupIds) {
            try {
                names.add(getGroupName(Long.parseLong(groupId)));
            } catch (NumberFormatException ignored) {
            }
        }
        return names;
    }

    /**
     * 根据用户ID获取其角色ID列表（用作 Flowable 候选组）
     */
    public List<String> getRoleIdsByUserId(Long userId) {
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(userId);
        List<String> roleIds = new ArrayList<>();
        for (SysRole role : roles) {
            roleIds.add(String.valueOf(role.getId()));
        }
        return roleIds;
    }
}
