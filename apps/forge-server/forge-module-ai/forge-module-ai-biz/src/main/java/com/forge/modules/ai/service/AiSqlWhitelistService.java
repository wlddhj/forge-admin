package com.forge.modules.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.ai.dto.request.SqlWhitelistQueryRequest;
import com.forge.modules.ai.dto.request.SqlWhitelistRequest;
import com.forge.modules.ai.dto.response.SqlWhitelistResponse;
import com.forge.modules.ai.entity.AiSqlWhitelist;

import java.util.List;

/**
 * AI Agent SQL 工具表白名单服务
 */
public interface AiSqlWhitelistService {

    Page<SqlWhitelistResponse> page(SqlWhitelistQueryRequest request);

    void add(SqlWhitelistRequest request);

    void update(SqlWhitelistRequest request);

    void remove(List<Long> ids);

    /**
     * 启用中的白名单表（本地缓存 10 分钟，写操作即时失效）
     */
    List<AiSqlWhitelist> getEnabledTables();
}
