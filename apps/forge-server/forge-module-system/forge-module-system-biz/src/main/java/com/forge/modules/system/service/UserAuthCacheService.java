package com.forge.modules.system.service;

import com.forge.modules.system.dto.user.CachedUserAuth;
import com.forge.modules.system.entity.SysUser;

/**
 * 用户认证上下文缓存服务
 *
 * JWT 过滤器认证路径的缓存唯一入口：
 * - 命中：认证路径零 DB 查询（仅 1 次 Redis 读取）
 * - miss：合并加载（用户仅查 1 次）并回写缓存
 * - 失效：用户级变更 evictUser；角色/菜单/部门级变更 evictAll（低频管理操作 + TTL 兜底）
 *
 * 缓存名 "userAuth" 在 CacheConfig 中注册（TTL 300s）且加入 ignoreCaches：
 * 租户段由本服务显式写入 key（{tenantId}:{username}），不走 TenantRedisCacheManager
 * 的租户名前缀——过滤器执行中途切换租户上下文，名前缀在缓存首次创建时烘焙会导致跨租户串缓存。
 *
 * @author standadmin
 */
public interface UserAuthCacheService {

    /**
     * 读取认证上下文，miss 时合并加载并回写缓存
     *
     * @param tenantId 租户 ID（JWT.tenantId，null 时 key 租户段取 0）
     * @param username 用户名（JWT.sub）
     * @return 认证上下文；用户不存在或已禁用返回 null（不缓存负结果）
     */
    CachedUserAuth getAuth(Long tenantId, String username);

    /**
     * 绕过缓存直接合并加载（OAuth2 服务账号路径用：tenantId 为 null 不走缓存）
     */
    CachedUserAuth loadFresh(SysUser user);

    /**
     * 用户级失效：用户信息/角色分配/状态/资料变更后调用
     */
    void evictUser(Long tenantId, String username);

    /**
     * 全量失效：角色/菜单/部门级变更影响多个用户，直接清空缓存
     * （低频管理操作，缓存体量小且有 5 分钟 TTL 兜底）
     */
    void evictAll();
}
