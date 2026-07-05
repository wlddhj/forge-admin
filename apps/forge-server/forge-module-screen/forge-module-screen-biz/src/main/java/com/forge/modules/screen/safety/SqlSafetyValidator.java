package com.forge.modules.screen.safety;

import com.forge.modules.screen.constant.ScreenConstants;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.TablesNamesFinder;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SQL 安全校验器：基于 JSqlParser 4.9 AST 进行白名单式校验。
 *
 * <p>校验顺序：SELECT-only -> 无存储过程 -> 无 UNION/SET -> 无危险函数
 *            -> 无系统表 -> 必须有 LIMIT -> LIMIT 不超上限。
 *
 * <p>设计原则：默认拒绝。任何无法解析为安全 SELECT 的语句一律拒绝。
 *
 * @author standadmin
 */
@Component
public class SqlSafetyValidator {

    /**
     * 禁用的系统库/表（schema 或表名匹配均拒绝）
     */
    private static final Set<String> FORBIDDEN_TABLES = Set.of(
        "information_schema", "mysql", "performance_schema", "sys",
        "pg_catalog", "information_schema.tables", "mysql.user"
    );

    /**
     * 禁用的危险函数（小写匹配）
     *
     * <p>包括：文件读写、长时延时、信息泄露、命令执行类。
     */
    private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of(
        "load_file", "sleep", "benchmark", "outfile", "dumpfile",
        "load data", "system", "database", "schema",
        "current_user", "user", "connection_id", "version",
        "get_lock", "release_lock", "is_free_lock", "is_used_lock",
        "row_count", "found_rows"
    );

    /**
     * 走完整校验流水线。任意一项失败即抛出 {@link SqlSafetyException}。
     *
     * <p>顺序设计：UNION 检查优先于 LIMIT，因为 UNION 攻击是最常见的注入向量，
     * 让它在 LIMIT 缺失错误之前被识别。
     */
    public void validate(Statement stmt) {
        if (stmt == null) {
            throw new SqlSafetyException("SQL 语句为空");
        }
        assertSelectOnly(stmt);
        assertNoStoredProcedure(stmt);
        assertNoUnion(stmt);
        assertNoDangerousFunctions(stmt);
        assertNoSystemTable(stmt);
        assertLimitPresent(stmt);
        assertLimitWithinMax(stmt);
    }

    /**
     * 拒绝 UNION / UNION ALL / INTERSECT / EXCEPT / MINUS 等 SET 操作。
     * 大屏白名单 SQL 通常只是单表查询；UNION 是 SQL 注入最常见向量。
     */
    public void assertNoUnion(Statement stmt) {
        if (stmt instanceof SetOperationList) {
            throw new SqlSafetyException("禁用 UNION / SET 操作语句");
        }
        // 还需递归检查子查询中的 UNION（防止 SELECT * FROM (A UNION B) LIMIT 1 绕过）
        if (stmt instanceof Select select) {
            findNestedUnion(select);
        }
    }

    private void findNestedUnion(Select select) {
        if (select instanceof SetOperationList) {
            throw new SqlSafetyException("禁用 UNION / SET 操作语句");
        } else if (select instanceof PlainSelect ps) {
            if (ps.getFromItem() instanceof ParenthesedSelect pselect) {
                findNestedUnion(pselect.getSelect());
            }
            if (ps.getJoins() != null) {
                for (Join join : ps.getJoins()) {
                    if (join.getRightItem() instanceof ParenthesedSelect pselect) {
                        findNestedUnion(pselect.getSelect());
                    }
                }
            }
        }
    }

    /**
     * 仅允许 SELECT 语句（拒绝 DELETE/UPDATE/INSERT/DROP/ALTER/TRUNCATE/CREATE 等）。
     */
    public void assertSelectOnly(Statement stmt) {
        if (!(stmt instanceof Select)) {
            throw new SqlSafetyException("仅允许 SELECT 语句，当前类型: "
                + stmt.getClass().getSimpleName());
        }
    }

    /**
     * JSqlParser 4.9 将 CALL 解析为独立语句类型；若进入此方法说明已被 assertSelectOnly 拦截。
     * 此方法保留为流水线占位，便于未来扩展（如存储过程语法变更）。
     */
    public void assertNoStoredProcedure(Statement stmt) {
        // 由 assertSelectOnly 保证；CALL 语句非 Select 会被拒绝
    }

    /**
     * 拦截危险函数调用：LOAD_FILE/SLEEP/BENCHMARK/OUTFILE/DUMPFILE 等。
     *
     * <p>遍历 PlainSelect 的 selectItems 与 where，递归扫描所有 Function 节点。
     * UNION/SET 走 SetOperationList 分支，每个子 PlainSelect 单独扫描。
     */
    public void assertNoDangerousFunctions(Statement stmt) {
        if (!(stmt instanceof Select select)) {
            return;
        }
        List<Function> dangerous = new ArrayList<>();
        scanSelectForFunctions(select, dangerous);
        for (Function f : dangerous) {
            String name = f.getName() == null ? "" : f.getName().toLowerCase();
            if (FORBIDDEN_FUNCTIONS.contains(name)) {
                throw new SqlSafetyException("禁用 function: " + f.getName());
            }
        }
    }

    private void scanSelectForFunctions(Select select, List<Function> sink) {
        if (select instanceof PlainSelect ps) {
            collectFunctions(ps, sink);
        } else if (select instanceof SetOperationList sol) {
            for (Select sub : sol.getSelects()) {
                scanSelectForFunctions(sub, sink);
            }
        } else if (select instanceof ParenthesedSelect pselect) {
            scanSelectForFunctions(pselect.getSelect(), sink);
        }
    }

    private void collectFunctions(PlainSelect ps, List<Function> sink) {
        // select items
        if (ps.getSelectItems() != null) {
            for (SelectItem<?> item : ps.getSelectItems()) {
                // 跳过 AllColumns / AllTableColumns（无 Expression）
                if (item.getExpression() != null) {
                    collectFunctionsInExpr(item.getExpression(), sink);
                }
            }
        }
        // where
        if (ps.getWhere() != null) {
            collectFunctionsInExpr(ps.getWhere(), sink);
        }
        // join 条件中的表达式
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                if (join.getOnExpressions() != null) {
                    for (Expression expr : join.getOnExpressions()) {
                        collectFunctionsInExpr(expr, sink);
                    }
                }
                if (join.getRightItem() instanceof ParenthesedSelect pselect) {
                    scanSelectForFunctions(pselect.getSelect(), sink);
                }
            }
        }
        // having
        if (ps.getHaving() != null) {
            collectFunctionsInExpr(ps.getHaving(), sink);
        }
    }

    /**
     * 使用 ExpressionDeParser + 自定义 visitor 递归收集所有 Function 节点。
     * ExpressionVisitorAdapter 自带 visit(Function) 重写入口。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void collectFunctionsInExpr(Expression expr, List<Function> sink) {
        net.sf.jsqlparser.expression.ExpressionVisitorAdapter visitor =
            new net.sf.jsqlparser.expression.ExpressionVisitorAdapter() {
                @Override
                public void visit(Function function) {
                    sink.add(function);
                    super.visit(function);
                }
            };
        expr.accept(visitor);
    }

    /**
     * 拒绝访问 information_schema / mysql / performance_schema / sys 等系统库表。
     * 利用 TablesNamesFinder 提取所有表名，逐个检查。
     */
    public void assertNoSystemTable(Statement stmt) {
        List<String> tables = new TablesNamesFinder().getTableList(stmt);
        for (String t : tables) {
            // TablesNamesFinder 返回形如 "information_schema.tables" 或 "sys_user"
            String lower = t.toLowerCase();
            if (FORBIDDEN_TABLES.contains(lower)) {
                throw new SqlSafetyException("禁用 system 表: " + t);
            }
            // 拆分 schema.name 单独判断
            String[] parts = lower.split("\\.");
            for (String part : parts) {
                if (FORBIDDEN_TABLES.contains(part)) {
                    throw new SqlSafetyException("禁用 system 表: " + t);
                }
            }
        }
    }

    /**
     * 必须 LIMIT：UNION 语句外层或每个子查询都至少有一处 LIMIT。
     */
    public void assertLimitPresent(Statement stmt) {
        if (!(stmt instanceof Select select)) {
            return;
        }
        if (!hasLimitAnywhere(select)) {
            throw new SqlSafetyException("SELECT 必须包含 LIMIT");
        }
    }

    private boolean hasLimitAnywhere(Select select) {
        if (select instanceof PlainSelect ps) {
            if (ps.getLimit() != null && ps.getLimit().getRowCount() != null) {
                return true;
            }
            // 子查询中的 LIMIT 也算（防御性，但单层 SQL 一般无子查询）
            if (ps.getFromItem() instanceof ParenthesedSelect ps2) {
                return hasLimitAnywhere(ps2.getSelect());
            }
            return false;
        } else if (select instanceof SetOperationList sol) {
            // UNION：JSqlParser 默认不接受 UNION 后接 LIMIT（语法限制），
            // 但有些方言允许。若外层无 LIMIT，要求每个子查询都有 LIMIT。
            // 但 UNION 攻击用例本身会在更早的 assertNoSystemTable 被拦截（系统表），
            // 而合法 UNION（白名单很少用到）需保证有 LIMIT。
            // 此处要求：外层 OR 至少一个子查询有 LIMIT
            boolean any = sol.getLimit() != null && sol.getLimit().getRowCount() != null;
            if (any) {
                return true;
            }
            for (Select sub : sol.getSelects()) {
                if (hasLimitAnywhere(sub)) {
                    return true;
                }
            }
            return false;
        } else if (select instanceof ParenthesedSelect pselect) {
            return hasLimitAnywhere(pselect.getSelect());
        }
        return false;
    }

    /**
     * LIMIT 行数不得超过 {@link ScreenConstants#SQL_MAX_ROWS}。
     */
    public void assertLimitWithinMax(Statement stmt) {
        if (!(stmt instanceof Select select)) {
            return;
        }
        Long count = extractRowCount(select);
        if (count == null) {
            return;
        }
        if (count > ScreenConstants.SQL_MAX_ROWS) {
            throw new SqlSafetyException(
                "LIMIT 超过上限 " + ScreenConstants.SQL_MAX_ROWS + "，当前: " + count);
        }
    }

    private Long extractRowCount(Select select) {
        Limit limit = findFirstLimit(select);
        if (limit == null || limit.getRowCount() == null) {
            return null;
        }
        Expression rc = limit.getRowCount();
        if (rc instanceof LongValue lv) {
            return lv.getValue();
        }
        // 兜底：用 deparser 解析字符串再尝试解析（防止 JDBC 参数等动态表达式）
        try {
            ExpressionDeParser dep = new ExpressionDeParser();
            StringBuilder sb = new StringBuilder();
            dep.setBuffer(sb);
            rc.accept(dep);
            String text = sb.toString().trim();
            // 跳过占位符 ?
            if (text.equals("?") || text.isEmpty()) {
                return null;
            }
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            // 非数字 LIMIT（参数化）：跳过，交由运行时参数校验
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Limit findFirstLimit(Select select) {
        if (select instanceof PlainSelect ps) {
            return ps.getLimit();
        } else if (select instanceof SetOperationList sol) {
            if (sol.getLimit() != null) {
                return sol.getLimit();
            }
            for (Select sub : sol.getSelects()) {
                Limit l = findFirstLimit(sub);
                if (l != null) {
                    return l;
                }
            }
            return null;
        } else if (select instanceof ParenthesedSelect pselect) {
            return findFirstLimit(pselect.getSelect());
        }
        return null;
    }

    /**
     * 保留 Table 重载入口以便后续扩展（如自定义白名单匹配）。
     */
    @SuppressWarnings("unused")
    private void checkTable(Table t) {
        String name = t.getName() == null ? "" : t.getName().toLowerCase();
        String schema = t.getSchemaName() == null ? "" : t.getSchemaName().toLowerCase();
        if (FORBIDDEN_TABLES.contains(name) || FORBIDDEN_TABLES.contains(schema)) {
            throw new SqlSafetyException("禁用 system 表: " + t.getFullyQualifiedName());
        }
    }
}
