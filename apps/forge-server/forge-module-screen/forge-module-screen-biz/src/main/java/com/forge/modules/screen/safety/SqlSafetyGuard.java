package com.forge.modules.screen.safety;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL 安全校验流水线协调器：注释硬拒 -> 解析 -> AST 校验 -> 表/列白名单校验。
 *
 * <p>协调 T8 {@link SqlSafetyValidator}（AST 安全规则）与 T9 {@link WhitelistService}
 * （表/列白名单），形成大屏 SQL 执行前的统一前置闸门。任意一步失败均抛出
 * {@link SqlSafetyException}。
 *
 * <h3>三道闸（安全堵口第一批）</h3>
 * <ul>
 *   <li><b>闸A 注释硬拒</b>：原始模板包含 {@code /*!}、{@code /*}、{@code --} 任一
 *       即拒绝（fail-closed）。字符串字面量中的形似字符（如 {@code 'a--b'}）会被
 *       误伤拒绝，属已确认接受的代价。</li>
 *   <li><b>闸B 全树表/列白名单</b>：表提取改用 {@link TablesNamesFinder} 全量提取
 *       （含 JOIN / 子查询中的表）逐一过表白名单；WHERE/HAVING/ORDER BY/GROUP BY/
 *       JOIN ON 上下文中的 {@link Column} 节点按所属表过列白名单，未知列 fail-closed。</li>
 *   <li><b>闸C LIMIT 参数名绑定</b>：收集 LIMIT/OFFSET 子句中命名占位符的参数名进
 *       {@link GuardResult#limitParamNames()}，交由 T11 运行时精确匹配实参校验上限。</li>
 * </ul>
 *
 * <h3>关键合约（C4 防御）</h3>
 * <p>{@link #guard(String, Map)} 返回 {@link GuardResult}，其中携带解析后的
 * {@link Statement} 而非原始 SQL 字符串。JSqlParser 在解析时已剥离注释，AST 上
 * 不存在注释构造；但闸A 仍对原始字符串硬拒，保证"注释"这一注入向量在进入解析前
 * 即被终结。
 *
 * <h3>列提取范围说明</h3>
 * <p>{@link #extractTablesAndColumns(Statement)} 仅做粗粒度提取（FROM 子句首 token +
 * 简单字段名）。T11 调用方传入的 {@code requestedColumnsByTable} 是 SELECT 列白名单的
 * 权威来源；AST 提取与全树列校验（闸B）作为补充，三者合并后送入 T9。
 *
 * @author standadmin
 */
@Component
@RequiredArgsConstructor
public class SqlSafetyGuard {

    private final SqlSafetyValidator validator;
    private final WhitelistService whitelist;

    /**
     * 注释硬拒（闸A）-> 解析 -> AST 校验 -> 全树表/列白名单（闸B）-> 收集 LIMIT 绑定参数名（闸C）。
     *
     * @param sql                     原始 SQL 字符串
     * @param requestedColumnsByTable T11 调用方提供的请求列映射（表名 -> 列名集合）
     * @return 解析后的 {@link GuardResult}（AST + LIMIT 绑定参数名）
     * @throws SqlSafetyException 含注释、解析失败、AST 规则失败或白名单失败均抛出
     */
    public GuardResult guard(String sql, Map<String, Set<String>> requestedColumnsByTable) {
        rejectTemplateComments(sql);
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new SqlSafetyException("SQL 解析失败: " + e.getMessage());
        }
        validator.validate(stmt);
        guardWhitelist(stmt, requestedColumnsByTable);
        return new GuardResult(stmt, collectLimitParamNames(stmt));
    }

    /**
     * 闸A：模板注释硬拒（fail-closed）。
     *
     * <p>原始模板包含 {@code /*!}（MySQL hint）、{@code /*}（块注释）、{@code --}
     * （行注释）任一即抛出。虽然 JSqlParser 解析时会剥离注释、执行走 AST 重序列化，
     * 但依赖"解析器永远剥得干净"是脆弱的（解析器升级/方言差异都可能引入残留），
     * 因此在解析之前对原始字符串直接终结该注入向量。
     *
     * <p>已知代价：字符串字面量中的形似字符（{@code WHERE name = 'a--b'}）会被
     * 误伤拒绝——用户已确认接受（fail-closed 优先）。
     */
    private void rejectTemplateComments(String sql) {
        if (sql == null) {
            return;
        }
        if (sql.contains("/*!") || sql.contains("/*") || sql.contains("--")) {
            throw new SqlSafetyException("SQL 模板不允许包含注释（/*! / /* / --）");
        }
    }

    /**
     * 合并 AST 提取的表/列与调用方提供的请求列，逐表送入白名单校验（闸B 入口）。
     */
    private void guardWhitelist(Statement stmt, Map<String, Set<String>> requested) {
        if (!(stmt instanceof Select)) {
            return;
        }
        String schema = System.getProperty("app.db.schema", "forge_admin");
        checkAllTablesAllowed(stmt, schema);
        Map<String, Set<String>> extracted = extractTablesAndColumns(stmt);
        Map<String, Set<String>> merged = mergeWithRequested(extracted, requested);
        merged.forEach((table, cols) -> whitelist.checkColumnsAllowed(schema, table, cols));
        checkTreeColumns(stmt, schema);
    }

    /**
     * 闸B-1：全树表白名单。
     *
     * <p>用 {@link TablesNamesFinder#getTableList(Statement)} 全量提取表名（覆盖
     * 主表 FROM、JOIN 右表、子查询中的表），逐一过 {@link WhitelistService#checkTableAllowed}；
     * 未命中白名单即 fail-closed。注意提取结果保留反引号/schema 前缀的原始写法
     * （与 {@link #extractTablesAndColumns} 的首 token 口径一致），未按白名单
     * 明文登记的名字天然被拒。
     */
    private void checkAllTablesAllowed(Statement stmt, String schema) {
        for (String table : new TablesNamesFinder().getTableList(stmt)) {
            if (whitelist.checkTableAllowed(schema, table) == null) {
                throw new SqlSafetyException("表不在白名单: " + schema + "." + table);
            }
        }
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
     * 闸B-2：全树列白名单——校验 WHERE/HAVING/ORDER BY/GROUP BY/JOIN ON 上下文中的
     * 所有 {@link Column} 节点。
     *
     * <p>列归属判定：带表限定（{@code t.col} / {@code sys_user.col}）→ 先按别名映射
     * 解析出真实表，无别名映射时把限定名当表名；不带限定 → 按该层 PlainSelect 的
     * 主表（FROM 首 token）。归属确定后按所属表过列白名单，白名单不含该列即抛
     * {@link SqlSafetyException}（fail-closed）。
     *
     * <p>子查询按作用域递归：外层 visitor 遇到标量子查询时，对其内部 PlainSelect
     * 以它自己的主表/别名映射重新走本方法，避免把子查询列错误归到外层主表。
     */
    private void checkTreeColumns(Statement stmt, String schema) {
        if (!(stmt instanceof Select sel)) {
            return;
        }
        if (sel.getSelectBody() instanceof PlainSelect ps) {
            checkTreeColumns(ps, schema);
        }
    }

    private void checkTreeColumns(PlainSelect ps, String schema) {
        String mainTable = ps.getFromItem() == null
            ? null : ps.getFromItem().toString().split(" ")[0];
        Map<String, String> aliasToTable = tableAliasMap(ps);
        List<Column> columns = new ArrayList<>();
        ExpressionVisitorAdapter visitor = new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                columns.add(column);
                super.visit(column);
            }

            @Override
            public void visit(ParenthesedSelect subSelect) {
                // 子查询列按其内部作用域校验（见类注释）
                if (subSelect.getSelect() instanceof PlainSelect inner) {
                    checkTreeColumns(inner, schema);
                }
                super.visit(subSelect);
            }

            @Override
            public void visit(Select select) {
                // JSqlParser 4.9 dispatch 陷阱：ParenthesedSelect 作为表达式出现时
                // 实际分发到 visit(Select)，两个入口都要接（同 SqlSafetyValidator C2）
                if (select instanceof ParenthesedSelect subSelect
                    && subSelect.getSelect() instanceof PlainSelect inner) {
                    checkTreeColumns(inner, schema);
                }
                super.visit(select);
            }
        };
        // where / having
        if (ps.getWhere() != null) {
            ps.getWhere().accept(visitor);
        }
        if (ps.getHaving() != null) {
            ps.getHaving().accept(visitor);
        }
        // select 项中的带限定列（u.password / sys_user.username）：
        // 纯标识符项已由 extractTablesAndColumns 覆盖，此处收带限定的 Column，防止 SELECT 面绕过列白名单
        if (ps.getSelectItems() != null) {
            for (SelectItem item : ps.getSelectItems()) {
                if (item.getExpression() != null) {
                    item.getExpression().accept(visitor);
                }
            }
        }
        // join on 条件
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                if (join.getOnExpressions() != null) {
                    for (Expression expr : join.getOnExpressions()) {
                        expr.accept(visitor);
                    }
                }
            }
        }
        // order by / group by
        if (ps.getOrderByElements() != null) {
            for (OrderByElement element : ps.getOrderByElements()) {
                if (element.getExpression() != null) {
                    element.getExpression().accept(visitor);
                }
            }
        }
        if (ps.getGroupBy() != null && ps.getGroupBy().getGroupByExpressionList() != null) {
            // JSqlParser 4.9 的 getGroupByExpressionList() 返回裸 ExpressionList，元素按 Object 迭代
            for (Object item : ps.getGroupBy().getGroupByExpressionList()) {
                if (item instanceof Expression expr) {
                    expr.accept(visitor);
                }
            }
        }

        // 按归属表分组后逐表校验（每表一次白名单查询）
        Map<String, Set<String>> byTable = new HashMap<>();
        for (Column column : columns) {
            String owner = resolveColumnOwner(column, aliasToTable, mainTable);
            if (owner == null) {
                continue;
            }
            byTable.computeIfAbsent(owner, k -> new HashSet<>()).add(column.getColumnName());
        }
        byTable.forEach((table, cols) -> whitelist.checkColumnsAllowed(schema, table, cols));
    }

    /**
     * 构建 别名 -> 真实表名 映射（主表与 JOIN 右表）。
     */
    private Map<String, String> tableAliasMap(PlainSelect ps) {
        Map<String, String> map = new HashMap<>();
        if (ps.getFromItem() instanceof Table table
            && table.getAlias() != null && table.getAlias().getName() != null) {
            map.put(table.getAlias().getName(), table.getName());
        }
        if (ps.getJoins() != null) {
            for (Join join : ps.getJoins()) {
                if (join.getRightItem() instanceof Table table
                    && table.getAlias() != null && table.getAlias().getName() != null) {
                    map.put(table.getAlias().getName(), table.getName());
                }
            }
        }
        return map;
    }

    /**
     * 解析 Column 的归属表：带限定 → 别名映射优先，否则限定名即表名；不带 → 主表。
     */
    private String resolveColumnOwner(Column column, Map<String, String> aliasToTable,
                                      String mainTable) {
        String qualifier = column.getTable() == null ? null : column.getTable().getName();
        if (qualifier == null || qualifier.isEmpty()) {
            return mainTable;
        }
        return aliasToTable.getOrDefault(qualifier, qualifier);
    }

    /**
     * 闸C：收集 LIMIT/OFFSET 子句中命名占位符（{@code :name}）的绑定参数名。
     *
     * <p>来源三处（与 {@link SqlSafetyValidator#assertLimitWithinMax} 的静态检查
     * 覆盖面保持一致）：{@code LIMIT :n} 行数、{@code LIMIT :off, :n} MySQL 逗号形式
     * 的 offset（均在 {@link Limit} 上）、{@code LIMIT 10 OFFSET :off} 独立 OFFSET
     * 子句（在 {@link PlainSelect#getOffset()} 上）。
     * 字面量 LIMIT/OFFSET 已由静态闸校验，不进本集合。
     */
    private Set<String> collectLimitParamNames(Statement stmt) {
        Set<String> names = new HashSet<>();
        if (!(stmt instanceof Select sel)) {
            return names;
        }
        if (sel.getSelectBody() instanceof PlainSelect ps) {
            Limit limit = ps.getLimit();
            if (limit != null) {
                addNamedParam(limit.getRowCount(), names);
                addNamedParam(limit.getOffset(), names);
            }
            if (ps.getOffset() != null) {
                addNamedParam(ps.getOffset().getOffset(), names);
            }
        }
        return names;
    }

    /**
     * 命名占位符表达式 -> 参数名；非命名占位符（字面量 / {@code ?}）不收集。
     */
    private void addNamedParam(Expression expr, Set<String> names) {
        if (expr instanceof JdbcNamedParameter param
            && param.getName() != null && !param.getName().isBlank()) {
            names.add(param.getName());
        }
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
