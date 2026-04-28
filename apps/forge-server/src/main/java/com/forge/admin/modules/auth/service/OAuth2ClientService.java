package com.forge.admin.modules.auth.service;

import com.forge.admin.modules.auth.dto.oauth2.ClientCreateRequest;
import com.forge.admin.modules.auth.dto.oauth2.ClientQueryRequest;
import com.forge.admin.modules.auth.dto.oauth2.ClientResponse;
import com.forge.admin.modules.auth.dto.oauth2.ClientUpdateRequest;

import java.util.List;

/**
 * OAuth2 客户端管理服务
 */
public interface OAuth2ClientService {

    /**
     * 分页查询客户端列表
     */
    List<ClientResponse> listClients(ClientQueryRequest request);

    /**
     * 根据ID查询客户端
     */
    ClientResponse getClient(String id);

    /**
     * 创建客户端，返回 client_secret（仅此一次可见）
     */
    String createClient(ClientCreateRequest request);

    /**
     * 更新客户端
     */
    void updateClient(ClientUpdateRequest request);

    /**
     * 删除客户端
     */
    void deleteClients(List<String> ids);

    /**
     * 重新生成客户端密钥
     */
    String regenerateSecret(String id);
}
