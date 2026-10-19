package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import com.forge.framework.localcache.LocalCaches;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * 含 tenant_id 列的表名缓存：SQL 工具租户注入判定依据（information_schema 探查，10 分钟）。
 * 探查失败抛异常（fail-closed）：租户过滤依赖此结果，静默降级会导致跨租户数据泄露。
 * Guava get(key, Callable) 同 key 原子加载（single-flight），替代原 synchronized 全局串行。
 */
@Component
public class TenantTableSchemaCache {

    private static final String CACHE_KEY = "tenant_tables";

    private final DataSource dataSource;
    private final Cache<String, Set<String>> cache;

    public TenantTableSchemaCache(DataSource dataSource, LocalCaches localCaches) {
        this.dataSource = dataSource;
        this.cache = localCaches.get("ai-tenant-tables",
                b -> b.expireAfterWrite(Duration.ofMinutes(10)));
    }

    public Set<String> getTenantTables() {
        try {
            return cache.get(CACHE_KEY, this::probeTables);
        } catch (ExecutionException | UncheckedExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("租户表结构探查失败，已拒绝执行: " + cause.getMessage());
        }
    }

    private Set<String> probeTables() {
        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT table_name FROM information_schema.columns " +
                             "WHERE table_schema = DATABASE() AND column_name = 'tenant_id'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1).toLowerCase());
                }
            }
        } catch (Exception e) {
            throw new BusinessException("租户表结构探查失败，已拒绝执行: " + e.getMessage());
        }
        return tables;
    }
}
