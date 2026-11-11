package com.forge.modules.system.service;

import com.forge.common.enumeration.DataScope;
import com.forge.modules.system.dto.user.CachedUserAuth;
import com.forge.modules.system.entity.SysRole;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysRoleDeptMapper;
import com.forge.modules.system.mapper.SysUserMapper;
import com.forge.modules.system.service.impl.UserAuthCacheServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAuthCacheServiceImpl 单测：缓存命中/miss 回写/用户级与全量失效/事务提交后失效
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
class UserAuthCacheServiceImplTest {

    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleDeptMapper sysRoleDeptMapper;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private SysRoleService sysRoleService;

    private UserAuthCacheServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAuthCacheServiceImpl(cacheManager, sysUserMapper, sysRoleDeptMapper);
        ReflectionTestUtils.setField(service, "sysUserService", sysUserService);
        ReflectionTestUtils.setField(service, "sysRoleService", sysRoleService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void mockCachePresent() {
        when(cacheManager.getCache("userAuth")).thenReturn(cache);
    }

    private SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId(100L);
        user.setUsername("zhangsan");
        user.setNickname("张三");
        user.setDeptId(10L);
        user.setTenantId(1L);
        user.setAccountType(0);
        user.setStatus(1);
        return user;
    }

    private void stubFreshLoad(SysUser user) {
        when(sysUserService.getUserPermissionCodes(user)).thenReturn(List.of("system:user:list"));
        when(sysUserMapper.selectRoleIdsByUserId(100L)).thenReturn(List.of(1L, 2L));

        SysRole allScope = new SysRole();
        allScope.setId(1L);
        allScope.setRoleCode("admin");
        allScope.setDataScope("1");
        SysRole customScope = new SysRole();
        customScope.setId(2L);
        customScope.setRoleCode("custom");
        customScope.setDataScope("2");
        when(sysRoleService.listByIds(List.of(1L, 2L))).thenReturn(List.of(allScope, customScope));
        when(sysRoleDeptMapper.selectDeptIdsByRoleId(2L)).thenReturn(List.of(10L, 11L));
    }

    @Test
    @DisplayName("缓存命中：直接返回缓存值，不查 DB")
    void getAuth_cacheHit_skipsDb() {
        mockCachePresent();
        CachedUserAuth cached = CachedUserAuth.builder().userId(100L).username("zhangsan").build();
        when(cache.get("1:zhangsan")).thenReturn(new SimpleValueWrapper(cached));

        CachedUserAuth result = service.getAuth(1L, "zhangsan");

        assertThat(result).isSameAs(cached);
        verify(sysUserMapper, never()).selectByUsernameSimple(any(), anyString());
    }

    @Test
    @DisplayName("缓存 miss：合并加载并回写缓存，二次调用不再查 DB")
    void getAuth_miss_loadsAndWritesBack() {
        mockCachePresent();
        when(cache.get("1:zhangsan")).thenReturn(null);
        SysUser user = activeUser();
        when(sysUserMapper.selectByUsernameSimple(1L, "zhangsan")).thenReturn(user);
        stubFreshLoad(user);

        CachedUserAuth first = service.getAuth(1L, "zhangsan");

        assertThat(first).isNotNull();
        assertThat(first.getUserId()).isEqualTo(100L);
        assertThat(first.getTenantId()).isEqualTo(1L);
        assertThat(first.getPermissionCodes()).containsExactly("system:user:list");
        // 最大数据权限取 ALL（level 最小）
        assertThat(first.getMaxDataScope()).isEqualTo(DataScope.ALL);
        // CUSTOM 角色带部门清单
        assertThat(first.getRoles()).hasSize(2);
        assertThat(first.getRoles().stream().filter(r -> r.getDataScope() == DataScope.CUSTOM).findFirst())
                .hasValueSatisfying(r -> assertThat(r.getDeptIds()).containsExactly(10L, 11L));

        verify(cache).put("1:zhangsan", first);

        // 回写后命中
        when(cache.get("1:zhangsan")).thenReturn(new SimpleValueWrapper(first));
        CachedUserAuth second = service.getAuth(1L, "zhangsan");
        assertThat(second).isSameAs(first);
        verify(sysUserMapper, times(1)).selectByUsernameSimple(1L, "zhangsan");
    }

    @Test
    @DisplayName("用户不存在：返回 null 且不写缓存")
    void getAuth_userNotFound_noCacheWrite() {
        mockCachePresent();
        when(cache.get("1:ghost")).thenReturn(null);
        when(sysUserMapper.selectByUsernameSimple(1L, "ghost")).thenReturn(null);

        assertThat(service.getAuth(1L, "ghost")).isNull();
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("用户已禁用：返回 null 且不写缓存")
    void getAuth_userDisabled_noCacheWrite() {
        mockCachePresent();
        when(cache.get("1:zhangsan")).thenReturn(null);
        SysUser user = activeUser();
        user.setStatus(0);
        when(sysUserMapper.selectByUsernameSimple(1L, "zhangsan")).thenReturn(user);

        assertThat(service.getAuth(1L, "zhangsan")).isNull();
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("tenantId 为 null：key 租户段取 0（服务账号语义，loadFresh 同样可用）")
    void loadFresh_withoutTenant() {
        SysUser user = activeUser();
        user.setTenantId(null);
        stubFreshLoad(user);

        CachedUserAuth auth = service.loadFresh(user);

        assertThat(auth.getTenantId()).isNull();
        assertThat(auth.getMaxDataScope()).isEqualTo(DataScope.ALL);
    }

    @Test
    @DisplayName("无事务上下文：evictUser 立即失效对应 key")
    void evictUser_noTransaction_evictsImmediately() {
        mockCachePresent();

        service.evictUser(1L, "zhangsan");

        verify(cache).evict("1:zhangsan");
    }

    @Test
    @DisplayName("事务激活：evictUser 推迟到提交后执行")
    void evictUser_inTransaction_defersToAfterCommit() {
        mockCachePresent();
        TransactionSynchronizationManager.initSynchronization();

        service.evictUser(1L, "zhangsan");

        // 事务内未提交：不应立即失效
        verify(cache, never()).evict(anyString());

        // 模拟事务提交：afterCommit 触发失效
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

        verify(cache).evict("1:zhangsan");
    }

    @Test
    @DisplayName("事务激活：evictAll 推迟到提交后清空缓存")
    void evictAll_inTransaction_defersToAfterCommit() {
        mockCachePresent();
        TransactionSynchronizationManager.initSynchronization();

        service.evictAll();

        verify(cache, never()).clear();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cache).clear();
    }
}
