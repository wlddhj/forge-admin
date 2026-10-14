package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.ai.entity.AiSqlWhitelist;
import com.forge.modules.ai.service.AiSqlWhitelistService;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SQL 工具租户改写器：JSqlParser AST 白名单校验 + 租户条件注入。
 *
 * 规则：
 * - 白名单外的表一律拒绝（错误信息附可用表清单）
 * - 含 tenant_id 列的白名单表：主 FROM 注入 WHERE、JOIN 右表注入 ON（保留 LEFT JOIN 语义）、
 *   子查询（FROM/IN/EXISTS/标量/ORDER BY/GROUP BY/HAVING）递归注入
 * - tenantId 以 Long 字面量内联（非字符串，无注入面）
 * - 解析失败、不支持的语法结构一律拒绝（fail-closed）
 * - 注入后全量后验：TablesNamesFinder 复核全部表名，任何租户表未被注入即拒绝
 *   （兜底表达式树中注入递归未覆盖的边角结构）
 */
@Component
@RequiredArgsConstructor
public class SqlTenantRewriter {

    private final AiSqlWhitelistService whitelistService;
    private final TenantTableSchemaCache tenantTableSchemaCache;
    private final TenantProperties tenantProperties;

    /**
     * 改写上下文：随递归传递，injectedTables 记录已注入租户条件的表（后验依据）
     */
    private record Context(Set<String> allowedTables, Long tenantId, Set<String> tenantTables,
                           Set<String> cteNames, Set<String> injectedTables) {
    }

    public String rewrite(String sql) {
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new BusinessException("SQL 解析失败，仅支持标准 SELECT 语句");
        }
        if (!(statement instanceof Select)) {
            throw new BusinessException("仅允许 SELECT 查询");
        }

        boolean tenantEnabled = Boolean.TRUE.equals(tenantProperties.getEnable());
        Long tenantId = null;
        if (tenantEnabled) {
            tenantId = TenantContextHolder.getTenantId();
            if (tenantId == null) {
                throw new BusinessException("租户上下文缺失，已拒绝执行 SQL 查询");
            }
        }

        Set<String> tenantTables = tenantEnabled ? tenantTableSchemaCache.getTenantTables() : Set.of();
        Context context = new Context(
                whitelistService.getEnabledTables().stream()
                        .map(w -> normalizeIdentifier(w.getTableName())).collect(Collectors.toSet()),
                tenantId, tenantTables, new HashSet<>(), new HashSet<>());

        processSelect((Select) statement, context);
        verify(statement, context);
        return statement.toString();
    }

    /**
     * 后验兜底：TablesNamesFinder 覆盖表达式树全部位置（含函数参数等注入递归未达角落），
     * 任何未过白名单或未注入租户过滤的表都使整条查询拒绝（宁可误拒不可泄露）。
     */
    private void verify(Statement statement, Context context) {
        for (String raw : new TablesNamesFinder().getTableList(statement)) {
            String tableName = normalizeIdentifier(raw);
            // schema.table 形式取表名末段
            int lastDot = tableName.lastIndexOf('.');
            if (lastDot >= 0) {
                tableName = tableName.substring(lastDot + 1);
            }
            if (context.cteNames().contains(tableName)) {
                continue;
            }
            if (!context.allowedTables().contains(tableName)) {
                throw new BusinessException("表 " + tableName + " 不在 SQL 白名单中。可用表: "
                        + String.join(", ", context.allowedTables()));
            }
            if (context.tenantTables().contains(tableName) && !context.injectedTables().contains(tableName)) {
                throw new BusinessException("查询包含无法自动注入租户过滤的结构（表 " + tableName
                        + "），已拒绝执行，请改写为更简单的查询");
            }
        }
    }

    private void processSelect(Select select, Context context) {
        List<WithItem> withItems = select.getWithItemsList();
        if (withItems != null) {
            // 4.9 中 WithItem 继承 ParenthesedSelect，CTE 名存于 alias
            for (WithItem withItem : withItems) {
                String name = withItem.getAlias() != null ? withItem.getAlias().getName() : null;
                if (name != null) {
                    context.cteNames().add(normalizeIdentifier(name));
                }
            }
            for (WithItem withItem : withItems) {
                processSelect(withItem, context);
            }
        }
        if (select instanceof SetOperationList setOperationList) {
            for (Select body : setOperationList.getSelects()) {
                processSelect(body, context);
            }
            return;
        }
        if (select instanceof ParenthesedSelect parenthesedSelect) {
            processSelect(parenthesedSelect.getSelect(), context);
            return;
        }
        if (!(select instanceof PlainSelect plainSelect)) {
            throw new BusinessException("不支持的查询结构: " + select.getClass().getSimpleName());
        }

        // 主 FROM 表：注入 WHERE
        Expression whereCondition = processFromItem(plainSelect.getFromItem(), context);
        if (whereCondition != null) {
            Expression where = plainSelect.getWhere();
            plainSelect.setWhere(where == null ? whereCondition : new AndExpression(whereCondition, where));
        }

        // JOIN 右表：注入 ON（保留 LEFT JOIN 语义）
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                processJoin(join, context);
            }
        }

        // WHERE / SELECT 列表 / HAVING / ORDER BY / GROUP BY 中的子查询递归
        processNestedSelects(plainSelect, context);
    }

    /**
     * 处理 FROM 项，返回该表应注入的租户条件（null = 无需注入）
     */
    private Expression processFromItem(FromItem fromItem, Context context) {
        if (fromItem instanceof Table table) {
            return processTable(table, context);
        }
        if (fromItem instanceof ParenthesedSelect parenthesedSelect) {
            processSelect(parenthesedSelect.getSelect(), context);
            return null;
        }
        if (fromItem instanceof LateralSubSelect lateralSubSelect) {
            processSelect(lateralSubSelect.getSelect(), context);
            return null;
        }
        throw new BusinessException("不支持的 FROM 结构: " + fromItem.getClass().getSimpleName());
    }

    private void processJoin(Join join, Context context) {
        FromItem right = join.getRightItem();
        if (right instanceof Table table) {
            Expression condition = processTable(table, context);
            if (condition != null) {
                // 合并为单个 AND 表达式：4.9 多元素 onExpressions 会输出多个 ON 片段（语法错误）
                List<Expression> all = new ArrayList<>();
                if (join.getOnExpressions() != null) {
                    all.addAll(join.getOnExpressions());
                }
                all.add(condition);
                Expression merged = all.get(0);
                for (int i = 1; i < all.size(); i++) {
                    merged = new AndExpression(merged, all.get(i));
                }
                join.setOnExpressions(List.of(merged));
            }
            return;
        }
        if (right instanceof ParenthesedSelect parenthesedSelect) {
            processSelect(parenthesedSelect.getSelect(), context);
            return;
        }
        if (right instanceof LateralSubSelect lateralSubSelect) {
            processSelect(lateralSubSelect.getSelect(), context);
            return;
        }
        throw new BusinessException("不支持的 JOIN 结构: " + right.getClass().getSimpleName());
    }

    /**
     * 单表处理：白名单校验 + 生成租户条件
     */
    private Expression processTable(Table table, Context context) {
        String tableName = normalizeIdentifier(table.getName());
        if (context.cteNames().contains(tableName)) {
            return null;
        }
        assertAllowed(tableName, context);
        if (context.tenantId() == null || !context.tenantTables().contains(tableName)) {
            return null;
        }
        context.injectedTables().add(tableName);
        String qualifier = table.getAlias() != null ? table.getAlias().getName() : table.getName();
        Column column = new Column(qualifier == null ? "tenant_id" : normalizeIdentifier(qualifier) + ".tenant_id");
        return new EqualsTo(column, new LongValue(context.tenantId()));
    }

    private void assertAllowed(String tableName, Context context) {
        if (!context.allowedTables().contains(tableName)) {
            throw new BusinessException("表 " + tableName + " 不在 SQL 白名单中。可用表: "
                    + String.join(", ", context.allowedTables()));
        }
    }

    /**
     * 递归处理 WHERE / SELECT 列表 / HAVING / ORDER BY / GROUP BY 表达式中的子查询。
     * 4.9 的 ParenthesedSelect.accept(ExpressionVisitor) 不回调 visit(ParenthesedSelect)，
     * 直接位于这些位置或 In/Exists 右侧的子查询须显式判断类型（未覆盖的边角由后验兜底拒绝）。
     */
    private void processNestedSelects(PlainSelect plainSelect, Context context) {
        ExpressionVisitorAdapter visitor = new ExpressionVisitorAdapter() {
            @Override
            public void visit(ParenthesedSelect subSelect) {
                processSelect(subSelect.getSelect(), context);
            }

            @Override
            public void visit(InExpression inExpression) {
                processPossibleSubSelect(inExpression.getRightExpression(), this);
                super.visit(inExpression);
            }

            @Override
            public void visit(ExistsExpression existsExpression) {
                processPossibleSubSelect(existsExpression.getRightExpression(), this);
                super.visit(existsExpression);
            }
        };
        acceptDeep(plainSelect.getWhere(), visitor);
        acceptDeep(plainSelect.getHaving(), visitor);
        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            acceptDeep(item.getExpression(), visitor);
        }
        if (plainSelect.getOrderByElements() != null) {
            for (OrderByElement orderBy : plainSelect.getOrderByElements()) {
                acceptDeep(orderBy.getExpression(), visitor);
            }
        }
        if (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupByExpressionList() != null) {
            for (Object expression : plainSelect.getGroupBy().getGroupByExpressionList()) {
                if (expression instanceof Expression expr) {
                    acceptDeep(expr, visitor);
                }
            }
        }
    }

    private void acceptDeep(Expression expression, ExpressionVisitorAdapter visitor) {
        if (expression instanceof ParenthesedSelect parenthesedSelect) {
            visitor.visit(parenthesedSelect);
        } else if (expression != null) {
            expression.accept(visitor);
        }
    }

    private void processPossibleSubSelect(Expression expression, ExpressionVisitorAdapter visitor) {
        if (expression instanceof ParenthesedSelect parenthesedSelect) {
            visitor.visit(parenthesedSelect);
        } else if (expression != null) {
            expression.accept(visitor);
        }
    }

    /**
     * 标识符归一化：剥离反引号/引号并小写（防引号写法绕过白名单，参照 screen 模块 C1 防御）
     */
    private String normalizeIdentifier(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("`", "").replace("\"", "").replace("'", "").toLowerCase();
    }
}
