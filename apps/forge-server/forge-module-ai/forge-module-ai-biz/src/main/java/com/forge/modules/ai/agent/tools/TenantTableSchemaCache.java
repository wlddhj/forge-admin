package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 含 tenant_id 列的表名缓存：SQL 工具租户注入判定依据（information_schema 探查，10 分钟）。
 * 探查失败抛异常（fail-closed）：租户过滤依赖此结果，静默降级会导致跨租户数据泄露。
 */
@Component
@RequiredArgsConstructor
public class TenantTableSchemaCache {

    private final DataSource dataSource;

    private volatile long cachedAt;
    private volatile Set<String> cachedTables = Set.of();

    public synchronized Set<String> getTenantTables() {
        if (System.currentTimeMillis() - cachedAt < 10 * 60 * 1000L && cachedAt > 0) {
            return cachedTables;
        }
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
        cachedTables = tables;
        cachedAt = System.currentTimeMillis();
        return tables;
    }
}
