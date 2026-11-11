package com.forge.modules.system.dto.user;

import com.forge.common.enumeration.DataScope;
import com.forge.common.utils.UserContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 用户认证上下文缓存载体
 *
 * JWT 过滤器认证路径的 Redis 缓存值：UserContext 全字段 + 权限码列表，
 * 缓存命中时认证路径零 DB 查询。不含 password hash（禁止入库）。
 * 权限码存 List<String>，SimpleGrantedAuthority 在组装 UserDetails 时现场构建，
 * 避免缓存序列化依赖 Spring Security 类型。
 *
 * @author standadmin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedUserAuth implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String nickname;

    private Long deptId;

    /**
     * 用户归属租户（以 DB 为准，非 JWT/header 租户）
     */
    private Long tenantId;

    /**
     * 账户类型（0:普通 1:管理员 2:平台超管）
     */
    private Integer accountType;

    /**
     * 最大数据权限范围
     */
    private DataScope maxDataScope;

    /**
     * 角色数据权限信息列表
     */
    private List<UserContext.DataScopeRoleInfo> roles;

    /**
     * 权限码列表（已含租户套餐过滤），供 @PreAuthorize 使用
     */
    private List<String> permissionCodes;
}
