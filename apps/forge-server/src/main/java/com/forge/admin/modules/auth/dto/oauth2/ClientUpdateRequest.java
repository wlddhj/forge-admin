package com.forge.admin.modules.auth.dto.oauth2;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * OAuth2 客户端更新请求
 */
@Data
public class ClientUpdateRequest {

    /** 主键ID */
    @NotBlank(message = "ID不能为空")
    private String id;

    /** 客户端名称 */
    private String clientName;

    /** 重定向URI列表 */
    private List<String> redirectUris;

    /** 授权类型 */
    private List<String> authorizationGrantTypes;

    /** 认证方法 */
    private List<String> clientAuthenticationMethods;

    /** 权限范围 */
    private List<String> scopes;

    /** Token 有效时间（秒） */
    private Integer accessTokenTimeToLive;

    /** Refresh Token 有效时间（秒） */
    private Integer refreshTokenTimeToLive;
}
