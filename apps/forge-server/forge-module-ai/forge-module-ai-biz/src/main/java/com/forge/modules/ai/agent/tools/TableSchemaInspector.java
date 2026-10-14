package com.forge.modules.ai.agent.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库表清单探查：为 Agent 决策提示词提供真实表名（含注释），避免模型臆造表名；缓存 10 分钟
 */
@Component
@RequiredArgsConstructor
public class TableSchemaInspector {

    private final DataSource dataSource;

    private volatile long cachedAt;
    private volatile String cachedDescription = "";

    public synchronized String describeTables() {
        if (System.currentTimeMillis() - cachedAt < 10 * 60 * 1000L && !cachedDescription.isEmpty()) {
            return cachedDescription;
        }
        StringBuilder sb = new StringBuilder();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT table_name, table_comment FROM information_schema.tables " +
                             "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                String comment = rs.getString(2);
                sb.append("- ").append(name);
                if (comment != null && !comment.isBlank()) {
                    sb.append("（").append(comment).append("）");
                }
                sb.append('\n');
            }
        } catch (Exception ignored) {
            // 探查失败不阻断决策，仅无表清单提示
        }
        cachedDescription = sb.toString();
        cachedAt = System.currentTimeMillis();
        return cachedDescription;
    }
}
