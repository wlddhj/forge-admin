package com.forge.modules.screen.safety;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SQL 安全校验流水线协调器：解析 -> AST 校验 -> 表/列白名单校验。
 *
 * <p>协调 T8 {@link SqlSafetyValidator}（AST 安全规则）与 T9 {@link WhitelistService}
 * （表/列白名单），形成大屏 SQL 执行前的统一前置闸门。任意一步失败均抛出
 * {@link SqlSafetyException}。
 *
 * <h3>关键合约（C4 防御）</h3>
 * <p>{@link #guard(String, Map)} 返回解析后的 {@link Statement} 对象，而非原始 SQL 字符串。
 * 这是为了让 T11 执行层使用 {@code Statement.toString()}（JSqlParser 重新序列化的结果）
 * 而非原始字符串执行 SQL —— JSqlParser 在解析时已剥离注释，因此即便攻击者在原始 SQL
 * 中嵌入 {@code /* SLEEP(5) *\/} 或 MySQL hint 形式注释，原始注释不会进入实际执行语句。
 * 若改为返回 String，T8 校验基于 AST（已脱注释），但 T11 用原始串执行就会形成绕过。
 *
 * <h3>列提取范围说明</h3>
 * <p>{@link #extractTablesAndColumns(Statement)} 仅做粗粒度提取（FROM 子句首 token +
 * 简单字段名）。这并非安全依赖：T11 调用方传入的 {@code requestedColumnsByTable} 是
 * 列级白名单的权威来源；AST 提取仅作为补充，二者合并后送入 T9。
 *
 * @author standadmin
 */
@Component
@RequiredArgsConstructor
public class SqlSafetyGuard {

    private final SqlSafetyValidator validator;
    private final WhitelistService whitelist;

    /**
     * 解析 SQL -> 校验 AST 安全规则 -> 校验表/列白名单。
     *
     * @param sql                     原始 SQL 字符串
     * @param requestedColumnsByTable T11 调用方提供的请求列映射（表名 -> 列名集合）
     * @return 解析后的 {@link Statement}，T11 应通过 {@code toString()} 重新序列化执行
     * @throws SqlSafetyException 解析失败、AST 规则失败或白名单失败均抛出
     */
    public Statement guard(String sql, Map<String, Set<String>> requestedColumnsByTable) {
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new SqlSafetyException("SQL 解析失败: " + e.getMessage());
        }
        validator.validate(stmt);
        guardWhitelist(stmt, requestedColumnsByTable);
        return stmt;
    }

    /**
     * 合并 AST 提取的表/列与调用方提供的请求列，逐表送入白名单校验。
     */
    private void guardWhitelist(Statement stmt, Map<String, Set<String>> requested) {
        if (!(stmt instanceof Select)) {
            return;
        }
        Map<String, Set<String>> extracted = extractTablesAndColumns(stmt);
        Map<String, Set<String>> merged = mergeWithRequested(extracted, requested);

        String schema = System.getProperty("app.db.schema", "forge_admin");
        merged.forEach((table, cols) -> whitelist.checkColumnsAllowed(schema, table, cols));
    }

    /**
     * 从 AST 中粗粒度提取 表 -> 列集合 映射。
     *
     * <p>提取规则：
     * <ul>
     *   <li>表名：FROM 子句 toString 后取首 token（覆盖 {@code table_a} 与 {@code schema.table_a}）</li>
     *   <li>列名：仅收集纯标识符 select 项（形如 {@code [a-z_][a-z0-9_]*}），
     *       含函数/表达式的项由 {@link SqlSafetyValidator} 单独校验</li>
     * </ul>
     * 列提取可能不完整（别名/JOIN 等场景），但 T11 调用方提供的
     * {@code requestedColumnsByTable} 是白名单权威来源，AST 提取仅作补充。
     */
    private Map<String, Set<String>> extractTablesAndColumns(Statement stmt) {
        Map<String, Set<String>> result = new HashMap<>();
        if (!(stmt instanceof Select sel)) {
            return result;
        }
        sel.getSelectBody().accept(new SelectVisitorAdapter() {
            @Override
            public void visit(PlainSelect ps) {
                if (ps.getFromItem() == null) {
                    return;
                }
                String tableName = ps.getFromItem().toString().split(" ")[0];
                Set<String> cols = result.computeIfAbsent(tableName, k -> new HashSet<>());
                if (ps.getSelectItems() != null) {
                    ps.getSelectItems().forEach(item -> {
                        // JSqlParser 4.9: SelectExpressionItem 已合并到 SelectItem
                        // 仅当存在 expression（即非 AllColumns / AllTableColumns）时收集
                        if (item.getExpression() != null) {
                            String s = item.toString().toLowerCase();
                            // 简单字段：直接放入；含函数/表达式则跳过（已由 SqlSafetyValidator 校验）
                            if (s.matches("^[a-z_][a-z0-9_]*$")) {
                                cols.add(s);
                            }
                        }
                    });
                }
            }
        });
        return result;
    }

    /**
     * 合并两个 表 -> 列集合 映射：同名列集合并集。
     */
    private Map<String, Set<String>> mergeWithRequested(Map<String, Set<String>> a,
                                                       Map<String, Set<String>> b) {
        Map<String, Set<String>> merged = new HashMap<>(a);
        b.forEach((k, v) -> merged.merge(k, v, (s1, s2) -> {
            s1.addAll(s2);
            return s1;
        }));
        return merged;
    }
}
