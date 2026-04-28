package com.forge.admin.modules.auth.dto.oauth2;

import lombok.Data;

/**
 * OAuth2 客户端查询请求
 */
@Data
public class ClientQueryRequest {

    /** 客户端ID（模糊搜索） */
    private String clientId;

    /** 客户端名称（模糊搜索） */
    private String clientName;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 10;
}
