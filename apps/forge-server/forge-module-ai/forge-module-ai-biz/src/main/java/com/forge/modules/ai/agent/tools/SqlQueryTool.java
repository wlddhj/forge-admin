package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL 查询工具：仅允许单条 SELECT、白名单表、自动注入租户过滤、强制 LIMIT、参数绑定；高危（调用前需人工确认）
 */
@Component
@RequiredArgsConstructor
public class SqlQueryTool implements AgentTool {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(union|into\\s+outfile|into\\s+dumpfile|sleep\\s*\\(|benchmark\\s*\\(|load_file\\s*\\(|extractvalue\\s*\\(|updatexml\\s*\\(|information_schema|performance_schema|mysql\\.|sys\\.)\\b",
            Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;
    private final AgentProperties properties;
    private final SqlTenantRewriter sqlTenantRewriter;

    @Override
    public String name() {
        return "sql_query";
    }

    @Override
    public String description() {
        return "对系统数据库执行只读 SELECT 查询（仅限白名单内的表，自动限定当前租户数据，自动追加 LIMIT 行数上限）";
    }

    @Override
    public Map<String, String> parameterSpec() {
        return Map.of("sql", "SELECT 语句（仅允许白名单表；禁止 UNION/写操作/危险函数；租户过滤自动注入，无需手写）", "params", "可选，占位参数数组");
    }

    @Override
    public boolean highRisk() {
        return true;
    }

    @Override
    public Object execute(Map<String, Object> args) {
        if (!properties.isSqlToolEnabled()) {
            throw new BusinessException("SQL 工具已被禁用");
        }
        String sql = normalizeSql(String.valueOf(args.get("sql")));
        validate(sql);
        sql = sqlTenantRewriter.rewrite(sql);

        List<Object> params = new ArrayList<>();
        Object rawParams = args.get("params");
        if (rawParams instanceof List<?> list) {
            params.addAll(list);
        }

        long start = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            statement.setMaxRows(properties.getSqlMaxRows());
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next() && rows.size() < properties.getSqlMaxRows()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        row.put(columns.get(i - 1), value == null ? null : String.valueOf(value));
                    }
                    rows.add(row);
                }
                return Map.of("columns", columns, "rowCount", rows.size(),
                        "rows", rows, "durationMs", System.currentTimeMillis() - start);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("SQL 执行失败: " + e.getMessage());
        }
    }

    private String normalizeSql(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("SQL 不能为空");
        }
        String sql = raw.trim().replaceAll(";+\\s*$", "");
        if (sql.contains(";")) {
            throw new BusinessException("仅允许单条 SQL 语句");
        }
        if (sql.contains("--") || sql.contains("#") || sql.contains("/*")) {
            throw new BusinessException("SQL 不允许包含注释");
        }
        String lower = sql.toLowerCase();
        if (!lower.startsWith("select")) {
            throw new BusinessException("仅允许 SELECT 查询");
        }
        // 强制 LIMIT 只看 select 列表之后的子句，避免列名/常量中的 "limit" 字样绕过
        String body = lower.substring(Math.max(lower.indexOf(" from "), 6));
        if (!body.contains("limit")) {
            sql = sql + " LIMIT " + properties.getSqlMaxRows();
        }
        return sql;
    }

    private void validate(String sql) {
        if (FORBIDDEN.matcher(sql).find()) {
            throw new BusinessException("SQL 包含被禁止的关键字（UNION/危险函数/系统库）");
        }
    }
}
