package com.forge.admin.modules.auth.dto.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OAuth2 客户端响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientResponse {

    /** 主键ID */
    private String id;

    /** 客户端ID */
    private String clientId;

    /** 客户端名称 */
    private String clientName;

    /** 客户端创建时间 */
    private String clientIdIssuedAt;

    /** 重定向URI列表 */
    private List<String> redirectUris;

    /** 授权类型 */
    private List<String> authorizationGrantTypes;

    /** 认证方法 */
    private List<String> clientAuthenticationMethods;

    /** 权限范围 */
    private List<String> scopes;
}
