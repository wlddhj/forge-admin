package com.forge.modules.ai.agent.tools;

import com.forge.framework.localcache.LocalCaches;
import com.google.common.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 表列名缓存：information_schema 探查（10 分钟），为决策提示词提供真实列名，
 * 避免模型臆造 created_at/updated_at 之类的列名烧掉工具步数。
 * 与 {@link TenantTableSchemaCache} 的 fail-closed 不同：列信息仅影响提示词质量，
 * 探查失败降级为空列（提示词退化为仅表名），不阻塞 Agent 执行；失败不回填缓存。
 */
@Slf4j
@Component
public class TableColumnSchemaCache {

    private final DataSource dataSource;
    private final Cache<String, List<String>> cache;

    public TableColumnSchemaCache(DataSource dataSource, LocalCaches localCaches) {
        this.dataSource = dataSource;
        this.cache = localCaches.get("ai-table-columns",
                b -> b.expireAfterWrite(Duration.ofMinutes(10)));
    }

    /**
     * 表的列名（按 ordinal_position 序）；探查失败或表不存在返回空列表（fail-open，不缓存失败）
     */
    public List<String> columnsOf(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return List.of();
        }
        try {
            return cache.get(tableName.toLowerCase(), () -> probeColumns(tableName));
        } catch (Exception e) {
            log.warn("表列名探查失败 table={}，提示词降级为仅表名: {}", tableName, e.getMessage());
            return List.of();
        }
    }

    private List<String> probeColumns(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT column_name FROM information_schema.columns " +
                             "WHERE table_schema = DATABASE() AND table_name = ? " +
                             "ORDER BY ordinal_position")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> columns = new ArrayList<>();
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
                return List.copyOf(columns);
            }
        }
    }
}
