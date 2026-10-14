package com.forge.modules.ai.dto.request;

import lombok.Data;

/**
 * SQL 白名单分页查询请求
 */
@Data
public class SqlWhitelistQueryRequest {

    private String tableName;

    private Integer enabled;

    private Long pageNum = 1L;

    private Long pageSize = 10L;
}
