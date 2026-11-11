package com.forge.modules.system.service.impl;

import com.forge.common.enumeration.DataScope;
import com.forge.common.utils.UserContext;
import com.forge.modules.system.dto.user.CachedUserAuth;
import com.forge.modules.system.entity.SysRole;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysRoleDeptMapper;
import com.forge.modules.system.mapper.SysUserMapper;
import com.forge.modules.system.service.SysRoleService;
import com.forge.modules.system.service.SysUserService;
import com.forge.modules.system.service.UserAuthCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户认证上下文缓存服务实现
 *
 * 合并加载逻辑：用户仅查 1 次（套餐过滤复用已加载实体），
 * 权限码 + 角色 + CUSTOM 数据权限部门一次装配，回写缓存供认证路径命中。
 *
 * @author standadmin
 */
@Slf4j
@Service
public class UserAuthCacheServiceImpl implements UserAuthCacheService {

    private static final String CACHE_NAME = "userAuth";

    /**
     * 服务账号（OAuth2 client credentials）无租户概念，JWT.tenantId 为 null 时 key 租户段取 0
     */
    private static final long NULL_TENANT_KEY_SEGMENT = 0L;

    private final CacheManager cacheManager;
    private final SysUserMapper sysUserMapper;
    private final SysRoleDeptMapper sysRoleDeptMapper;

    @Lazy
    @Autowired
    private SysUserService sysUserService;

    @Lazy
    @Autowired
    private SysRoleService sysRoleService;

    public UserAuthCacheServiceImpl(CacheManager cacheManager,
                                    SysUserMapper sysUserMapper,
                                    SysRoleDeptMapper sysRoleDeptMapper) {
        this.cacheManager = cacheManager;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleDeptMapper = sysRoleDeptMapper;
    }

    @Override
    public CachedUserAuth getAuth(Long tenantId, String username) {
        String key = buildKey(tenantId, username);
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(key);
            Object value = wrapper != null ? wrapper.get() : null;
            if (value instanceof CachedUserAuth cached) {
                return cached;
            }
        }

        // miss：合并加载并回写
        SysUser user = sysUserMapper.selectByUsernameSimple(tenantId, username);
        if (user == null) {
            log.warn("[认证缓存] 用户不存在，不缓存: tenantId={}, username={}", tenantId, username);
            return null;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("[认证缓存] 用户已禁用，不缓存: tenantId={}, username={}", tenantId, username);
            return null;
        }

        CachedUserAuth auth = loadFresh(user);
        if (cache != null) {
            cache.put(key, auth);
        }
        return auth;
    }

    @Override
    public CachedUserAuth loadFresh(SysUser user) {
        // 1. 权限码（套餐过滤复用已加载实体，不再重查用户）
        List<String> permissionCodes = sysUserService.getUserPermissionCodes(user);

        // 2. 角色与数据权限
        List<Long> roleIds = sysUserMapper.selectRoleIdsByUserId(user.getId());
        List<SysRole> roles = (roleIds == null || roleIds.isEmpty())
                ? Collections.emptyList()
                : sysRoleService.listByIds(roleIds);

        DataScope maxDataScope = calculateMaxDataScope(roles);
        List<UserContext.DataScopeRoleInfo> roleInfos = toRoleInfos(roles);

        return CachedUserAuth.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .deptId(user.getDeptId())
                .tenantId(user.getTenantId())
                .accountType(user.getAccountType())
                .maxDataScope(maxDataScope)
                .roles(roleInfos)
                .permissionCodes(permissionCodes)
                .build();
    }

    @Override
    public void evictUser(Long tenantId, String username) {
        // 钩子位于 @Transactional 方法内：提交前失效会被并发请求以未提交的旧数据回填，
        // 必须等事务提交后再失效（无事务上下文时立即失效）
        doEvictAfterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.evict(buildKey(tenantId, username));
                log.debug("[认证缓存] 用户级失效: tenantId={}, username={}", tenantId, username);
            }
        });
    }

    @Override
    public void evictAll() {
        doEvictAfterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.clear();
                log.debug("[认证缓存] 全量失效（角色/菜单/部门级变更）");
            }
        });
    }

    /**
     * 事务感知失效：事务同步激活时挂到 afterCommit，否则立即执行
     */
    private void doEvictAfterCommit(Runnable evictAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAction.run();
                }
            });
        } else {
            evictAction.run();
        }
    }

    private String buildKey(Long tenantId, String username) {
        long tenantSegment = tenantId != null ? tenantId : NULL_TENANT_KEY_SEGMENT;
        return tenantSegment + ":" + username;
    }

    private DataScope calculateMaxDataScope(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return DataScope.SELF;
        }
        // DataScope 约定数值越小权限越大（ALL=1…SELF=5），
        // 取 level 最小者即最大权限（修正原过滤器 .max 的取向反转）
        return roles.stream()
                .map(r -> {
                    try {
                        return DataScope.fromCode(r.getDataScope());
                    } catch (Exception e) {
                        log.warn("[认证缓存] 无效的数据权限值: {}", r.getDataScope());
                        return DataScope.SELF;
                    }
                })
                .min(Comparator.comparing(DataScope::getLevel))
                .orElse(DataScope.SELF);
    }

    private List<UserContext.DataScopeRoleInfo> toRoleInfos(List<SysRole> roles) {
        return roles.stream()
                .map(r -> {
                    UserContext.DataScopeRoleInfo info = new UserContext.DataScopeRoleInfo();
                    info.setRoleId(r.getId());
                    info.setRoleCode(r.getRoleCode());
                    info.setDataScope(DataScope.fromCode(r.getDataScope()));
                    if (DataScope.CUSTOM.equals(info.getDataScope())) {
                        info.setDeptIds(sysRoleDeptMapper.selectDeptIdsByRoleId(r.getId()));
                    }
                    return info;
                })
                .collect(Collectors.toList());
    }
}
