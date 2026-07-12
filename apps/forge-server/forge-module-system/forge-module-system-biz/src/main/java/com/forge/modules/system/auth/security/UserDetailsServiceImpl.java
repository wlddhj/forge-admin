package com.forge.modules.system.auth.security;

import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户详情服务实现
 *
 * @author standadmin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserService sysUserService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("[登录] 尝试登录用户: {}", username);

        // 认证阶段 UserContext 尚未设置，传入 null 按 username 查找
        // TODO: Task 19 将修改 AuthServiceImpl.login 先查租户再查用户，届时需同步更新
        SysUser user = sysUserService.getByUsername(null, username);
        if (user == null) {
            log.warn("[登录] 用户不存在: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        log.info("[登录] 用户信息: username={}, status={}, deleted={}, accountType={}",
                user.getUsername(), user.getStatus(), user.getDeleted(), user.getAccountType());
        log.info("[登录] 数据库密码hash: {}", user.getPassword());

        if (user.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 获取用户权限
        List<SimpleGrantedAuthority> authorities = sysUserService.getUserPermissions(user.getId());

        UserDetails userDetails = User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(user.getStatus() != 1)
                .build();

        log.info("[登录] UserDetails已创建, username={}, passwordLength={}, authoritiesCount={}",
                userDetails.getUsername(), userDetails.getPassword().length(), authorities.size());

        return userDetails;
    }
}
