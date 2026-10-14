package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.dto.request.SqlWhitelistQueryRequest;
import com.forge.modules.ai.dto.request.SqlWhitelistRequest;
import com.forge.modules.ai.dto.response.SqlWhitelistResponse;
import com.forge.modules.ai.entity.AiSqlWhitelist;
import com.forge.modules.ai.mapper.AiSqlWhitelistMapper;
import com.forge.modules.ai.service.AiSqlWhitelistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL 白名单服务实现：CRUD + 启用表本地缓存（跨租户共享配置，多实例部署下其他实例最迟 10 分钟生效）
 */
@Service
@RequiredArgsConstructor
public class AiSqlWhitelistServiceImpl implements AiSqlWhitelistService {

    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final AiSqlWhitelistMapper whitelistMapper;

    private volatile long cachedAt;
    private volatile List<AiSqlWhitelist> enabledCache = List.of();

    @Override
    public Page<SqlWhitelistResponse> page(SqlWhitelistQueryRequest request) {
        Page<AiSqlWhitelist> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<AiSqlWhitelist> wrapper = new LambdaQueryWrapper<AiSqlWhitelist>()
                .like(request.getTableName() != null && !request.getTableName().isBlank(),
                        AiSqlWhitelist::getTableName, request.getTableName().trim())
                .eq(request.getEnabled() != null, AiSqlWhitelist::getEnabled, request.getEnabled())
                .orderByAsc(AiSqlWhitelist::getTableName);
        Page<AiSqlWhitelist> result = whitelistMapper.selectPage(page, wrapper);
        List<SqlWhitelistResponse> records = result.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        Page<SqlWhitelistResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    public void add(SqlWhitelistRequest request) {
        String tableName = normalize(request.getTableName());
        checkDuplicate(tableName, null);
        AiSqlWhitelist entity = new AiSqlWhitelist();
        entity.setTableName(tableName);
        entity.setTableComment(request.getTableComment());
        entity.setEnabled(request.getEnabled());
        entity.setRemark(request.getRemark());
        whitelistMapper.insert(entity);
        evictCache();
    }

    @Override
    public void update(SqlWhitelistRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("ID 不能为空");
        }
        AiSqlWhitelist existing = whitelistMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException("白名单记录不存在");
        }
        String tableName = normalize(request.getTableName());
        checkDuplicate(tableName, request.getId());
        AiSqlWhitelist entity = new AiSqlWhitelist();
        entity.setId(request.getId());
        entity.setTableName(tableName);
        entity.setTableComment(request.getTableComment());
        entity.setEnabled(request.getEnabled());
        entity.setRemark(request.getRemark());
        whitelistMapper.updateById(entity);
        evictCache();
    }

    @Override
    public void remove(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        whitelistMapper.deleteByIds(ids);
        evictCache();
    }

    @Override
    public List<AiSqlWhitelist> getEnabledTables() {
        long now = System.currentTimeMillis();
        List<AiSqlWhitelist> cache = enabledCache;
        if (cachedAt == 0 || now - cachedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (cachedAt == 0 || now - cachedAt > CACHE_TTL_MS) {
                    enabledCache = whitelistMapper.selectList(new LambdaQueryWrapper<AiSqlWhitelist>()
                            .eq(AiSqlWhitelist::getEnabled, 1)
                            .orderByAsc(AiSqlWhitelist::getTableName));
                    cachedAt = now;
                    cache = enabledCache;
                }
            }
        }
        return cache;
    }

    private String normalize(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new BusinessException("表名不能为空");
        }
        return tableName.trim().toLowerCase();
    }

    private void checkDuplicate(String tableName, Long excludeId) {
        AiSqlWhitelist existing = whitelistMapper.selectOne(new LambdaQueryWrapper<AiSqlWhitelist>()
                .eq(AiSqlWhitelist::getTableName, tableName).last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("表 " + tableName + " 已在白名单中");
        }
    }

    private void evictCache() {
        synchronized (this) {
            enabledCache = List.of();
            cachedAt = 0;
        }
    }

    private SqlWhitelistResponse toResponse(AiSqlWhitelist entity) {
        SqlWhitelistResponse response = new SqlWhitelistResponse();
        response.setId(entity.getId());
        response.setTableName(entity.getTableName());
        response.setTableComment(entity.getTableComment());
        response.setEnabled(entity.getEnabled());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }
}
