package com.forge.admin.modules.auth.dto.oauth2;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * OAuth2 客户端创建请求
 */
@Data
public class ClientCreateRequest {

    /** 客户端ID */
    @NotBlank(message = "客户端ID不能为空")
    private String clientId;

    /** 客户端名称 */
    @NotBlank(message = "客户端名称不能为空")
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
