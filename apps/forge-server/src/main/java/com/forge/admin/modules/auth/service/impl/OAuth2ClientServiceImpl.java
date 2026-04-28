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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ClientResponse> listClients(ClientQueryRequest request) {
        StringBuilder sql = new StringBuilder("SELECT id FROM oauth2_registered_client WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (StringUtils.hasText(request.getClientId())) {
            sql.append(" AND client_id LIKE ?");
            params.add("%" + request.getClientId() + "%");
        }
        if (StringUtils.hasText(request.getClientName())) {
            sql.append(" AND client_name LIKE ?");
            params.add("%" + request.getClientName() + "%");
        }
        sql.append(" ORDER BY client_id_issued_at DESC");

        List<String> ids = jdbcTemplate.queryForList(sql.toString(), params.toArray(), String.class);
        return ids.stream()
                .map(id -> registeredClientRepository.findById(id))
                .filter(java.util.Objects::nonNull)
                .map(this::toResponse)
                .toList();
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

        // 重定向URI（authorization_code 模式必须提供）
        if (request.getRedirectUris() != null) {
            for (String uri : request.getRedirectUris()) {
                if (uri != null && !uri.isBlank()) {
                    builder.redirectUri(uri);
                }
            }
        }
        // 如果授权类型包含 authorization_code 但未提供 redirectUri，设置默认值
        boolean hasAuthCode = request.getAuthorizationGrantTypes() != null
                && request.getAuthorizationGrantTypes().contains("authorization_code");
        if (hasAuthCode && (request.getRedirectUris() == null || request.getRedirectUris().stream().allMatch(u -> u == null || u.isBlank()))) {
            builder.redirectUri("http://localhost:8080/callback");
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

        // 重建完整的 RegisteredClient（from 会复制所有字段，然后覆盖修改项）
        RegisteredClient.Builder builder = RegisteredClient.from(existing);

        if (StringUtils.hasText(request.getClientName())) {
            builder.clientName(request.getClientName());
        }

        // 授权类型
        if (request.getAuthorizationGrantTypes() != null) {
            builder.authorizationGrantTypes(grantTypes -> {
                grantTypes.clear();
                for (String grantType : request.getAuthorizationGrantTypes()) {
                    grantTypes.add(new AuthorizationGrantType(grantType));
                }
            });
        }

        // 认证方法
        if (request.getClientAuthenticationMethods() != null) {
            builder.clientAuthenticationMethods(methods -> {
                methods.clear();
                for (String method : request.getClientAuthenticationMethods()) {
                    methods.add(new ClientAuthenticationMethod(method));
                }
            });
        }

        // Scopes
        if (request.getScopes() != null) {
            builder.scopes(scopes -> {
                scopes.clear();
                scopes.addAll(request.getScopes());
            });
        }

        // 重定向URI
        if (request.getRedirectUris() != null) {
            builder.redirectUris(uris -> {
                uris.clear();
                for (String uri : request.getRedirectUris()) {
                    if (uri != null && !uri.isBlank()) {
                        uris.add(uri);
                    }
                }
            });
        }

        // Token 设置
        TokenSettings.Builder tokenSettingsBuilder = TokenSettings.builder();
        tokenSettingsBuilder.accessTokenTimeToLive(
                request.getAccessTokenTimeToLive() != null
                        ? Duration.ofSeconds(request.getAccessTokenTimeToLive())
                        : existing.getTokenSettings().getAccessTokenTimeToLive());
        tokenSettingsBuilder.refreshTokenTimeToLive(
                request.getRefreshTokenTimeToLive() != null
                        ? Duration.ofSeconds(request.getRefreshTokenTimeToLive())
                        : existing.getTokenSettings().getRefreshTokenTimeToLive());
        builder.tokenSettings(tokenSettingsBuilder.build());

        registeredClientRepository.save(builder.build());
        log.info("更新OAuth2客户端: id={}", request.getId());
    }

    @Override
    public void deleteClients(List<String> ids) {
        for (String id : ids) {
            jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", id);
            jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", id);
            jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
        }
        log.info("删除OAuth2客户端: ids={}", ids);
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
