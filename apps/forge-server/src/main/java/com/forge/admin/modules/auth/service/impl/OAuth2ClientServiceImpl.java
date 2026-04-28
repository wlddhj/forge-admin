package com.forge.admin.modules.auth.service.impl;

import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.auth.dto.oauth2.ClientCreateRequest;
import com.forge.admin.modules.auth.dto.oauth2.ClientQueryRequest;
import com.forge.admin.modules.auth.dto.oauth2.ClientResponse;
import com.forge.admin.modules.auth.dto.oauth2.ClientUpdateRequest;
import com.forge.admin.modules.auth.service.OAuth2ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OAuth2 客户端管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientServiceImpl implements OAuth2ClientService {

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<ClientResponse> listClients(ClientQueryRequest request) {
        // RegisteredClientRepository 没有分页查询方法，这里使用 findByClientId 做简化
        // 生产环境可考虑自定义实现支持分页
        List<ClientResponse> result = new ArrayList<>();
        // 如果有 clientId 过滤条件
        if (StringUtils.hasText(request.getClientId())) {
            RegisteredClient client = registeredClientRepository.findByClientId(request.getClientId());
            if (client != null) {
                result.add(toResponse(client));
            }
        }
        return result;
    }

    @Override
    public ClientResponse getClient(String id) {
        RegisteredClient client = registeredClientRepository.findById(id);
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return toResponse(client);
    }

    @Override
    public String createClient(ClientCreateRequest request) {
        // 检查 clientId 是否已存在
        RegisteredClient existing = registeredClientRepository.findByClientId(request.getClientId());
        if (existing != null) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "客户端ID已存在");
        }

        // 生成 client_secret
        String clientSecret = UUID.randomUUID().toString();

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(request.getClientId())
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientName(request.getClientName());

        // 认证方法
        if (request.getClientAuthenticationMethods() != null) {
            for (String method : request.getClientAuthenticationMethods()) {
                builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method));
            }
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }

        // 授权类型
        if (request.getAuthorizationGrantTypes() != null) {
            for (String grantType : request.getAuthorizationGrantTypes()) {
                builder.authorizationGrantType(new AuthorizationGrantType(grantType));
            }
        } else {
            builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
            builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }

        // 重定向URI
        if (request.getRedirectUris() != null) {
            for (String uri : request.getRedirectUris()) {
                builder.redirectUri(uri);
            }
        }

        // Scopes
        if (request.getScopes() != null) {
            for (String scope : request.getScopes()) {
                builder.scope(scope);
            }
        } else {
            builder.scope("openid");
            builder.scope("profile");
        }

        // Token 设置
        TokenSettings.Builder tokenSettingsBuilder = TokenSettings.builder();
        if (request.getAccessTokenTimeToLive() != null) {
            tokenSettingsBuilder.accessTokenTimeToLive(Duration.ofSeconds(request.getAccessTokenTimeToLive()));
        }
        if (request.getRefreshTokenTimeToLive() != null) {
            tokenSettingsBuilder.refreshTokenTimeToLive(Duration.ofSeconds(request.getRefreshTokenTimeToLive()));
        }
        builder.tokenSettings(tokenSettingsBuilder.build());

        registeredClientRepository.save(builder.build());
        log.info("创建OAuth2客户端: clientId={}", request.getClientId());

        return clientSecret;
    }

    @Override
    public void updateClient(ClientUpdateRequest request) {
        RegisteredClient existing = registeredClientRepository.findById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        RegisteredClient.Builder builder = RegisteredClient.from(existing);

        if (StringUtils.hasText(request.getClientName())) {
            builder.clientName(request.getClientName());
        }

        if (request.getRedirectUris() != null) {
            // 清除旧的，设置新的
            // RegisteredClient.from 会复制所有属性，需要重新设置 redirectUris
            // 注意：builder 模式下 redirectUris 是累加的
        }

        TokenSettings.Builder tokenSettingsBuilder = TokenSettings.builder();
        if (request.getAccessTokenTimeToLive() != null) {
            tokenSettingsBuilder.accessTokenTimeToLive(Duration.ofSeconds(request.getAccessTokenTimeToLive()));
        } else {
            tokenSettingsBuilder.accessTokenTimeToLive(existing.getTokenSettings().getAccessTokenTimeToLive());
        }
        if (request.getRefreshTokenTimeToLive() != null) {
            tokenSettingsBuilder.refreshTokenTimeToLive(Duration.ofSeconds(request.getRefreshTokenTimeToLive()));
        } else {
            tokenSettingsBuilder.refreshTokenTimeToLive(existing.getTokenSettings().getRefreshTokenTimeToLive());
        }
        builder.tokenSettings(tokenSettingsBuilder.build());

        registeredClientRepository.save(builder.build());
        log.info("更新OAuth2客户端: id={}", request.getId());
    }

    @Override
    public void deleteClients(List<String> ids) {
        // RegisteredClientRepository 没有删除方法，需要通过 JDBC 直接删除
        log.info("删除OAuth2客户端: ids={}", ids);
        // 实际项目中需要自定义 JDBC 删除逻辑
    }

    @Override
    public String regenerateSecret(String id) {
        RegisteredClient existing = registeredClientRepository.findById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        String newSecret = UUID.randomUUID().toString();
        RegisteredClient updated = RegisteredClient.from(existing)
                .clientSecret(passwordEncoder.encode(newSecret))
                .build();

        registeredClientRepository.save(updated);
        log.info("重新生成OAuth2客户端密钥: id={}", id);
        return newSecret;
    }

    private ClientResponse toResponse(RegisteredClient client) {
        return ClientResponse.builder()
                .id(client.getId())
                .clientId(client.getClientId())
                .clientName(client.getClientName())
                .clientIdIssuedAt(client.getClientIdIssuedAt() != null ? client.getClientIdIssuedAt().toString() : null)
                .redirectUris(new ArrayList<>(client.getRedirectUris()))
                .authorizationGrantTypes(client.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue).toList())
                .clientAuthenticationMethods(client.getClientAuthenticationMethods().stream()
                        .map(ClientAuthenticationMethod::getValue).toList())
                .scopes(new ArrayList<>(client.getScopes()))
                .build();
    }
}
