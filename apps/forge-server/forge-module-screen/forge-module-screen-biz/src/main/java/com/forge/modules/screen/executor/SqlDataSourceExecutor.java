package com.forge.modules.screen.executor;

import com.forge.modules.screen.constant.ScreenConstants;
import com.forge.modules.screen.safety.SqlSafetyException;
import com.forge.modules.screen.safety.SqlSafetyGuard;
import com.forge.modules.screen.util.SqlParamBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 大屏 SQL 数据源执行器：基于 {@link JdbcTemplate} 的参数化执行。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li><b>安全闸门</b>（C4 闭合）：先调用 {@link SqlSafetyGuard#guard}，得到
 *       AST 重序列化后的 {@link Statement}；真正用于 JDBC 执行的 SQL 取自
 *       {@code stmt.toString()}，<b>绝不</b>使用原始 sqlTemplate 字符串。
 *       这样攻击者即便在原始 SQL 注入 {@code /* SLEEP(5) *\/} 或 MySQL hint 形式注释，
 *       JSqlParser 在解析阶段已剥离，deparsed 形式不会包含这些构造。</li>
 *   <li><b>参数绑定</b>（C3 闭合）：通过 {@link SqlParamBinder} 把 {@code :name}
 *       转换为 JDBC 占位符，所有外部值经 {@code PreparedStatement} 绑定，绝不字符串拼接。</li>
 *   <li><b>运行时 LIMIT/OFFSET 上限</b>（C5 闭合）：T8 静态 AST 校验放过
 *       {@code LIMIT ?} 占位符（无法静态确定绑定值），由本执行器在运行时
 *       通过 {@link #enforceBoundLimit} 检查参数名中含 {@code limit}/{@code offset}
 *       的实参值是否超过 {@link ScreenConstants#SQL_MAX_ROWS}。</li>
 *   <li><b>查询超时</b>：每次执行前设置 {@link JdbcTemplate#setQueryTimeout}，
 *       超时秒数 = {@code timeoutMs / 1000}。</li>
 * </ul>
 *
 * <h3>缺省依赖</h3>
 * <p>{@code jdbcTemplate} 通过构造函数注入。若上层模块尚未提供数据源，
 * 仍可使用 mock 形式构造（部分单元测试传 {@code null}）。本类仅在真正执行
 * {@code execute(...)} 时访问 jdbcTemplate，故 mock 注入下只要不触发执行就不会 NPE。
 *
 * <p>所有外部失败统一抛出 {@link SqlSafetyException}（包括安全闸门失败与
 * 运行时上限失败），便于上层 Controller 用同一异常处理器统一映射 4xx。
 *
 * @author standadmin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlDataSourceExecutor {

    private final SqlSafetyGuard safetyGuard;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行参数化 SQL 查询。
     *
     * @param sqlTemplate 原始 SQL 模板，含 {@code :name} 命名占位符
     * @param params      参数名 -> 参数值映射（可能包含 limit/offset 等）
     * @param timeoutMs   查询超时毫秒数（被转换为秒后传入 jdbcTemplate）
     * @return 查询结果，每行一个 {@code Map<String,Object>}
     * @throws SqlSafetyException 当安全闸门失败或 LIMIT/OFFSET 实参超过上限时
     */
    public List<Map<String, Object>> execute(String sqlTemplate, Map<String, Object> params, int timeoutMs) {
        // ① 安全闸门：解析 + AST 校验 + 白名单。返回已 deparsed 的 Statement。
        Statement stmt = safetyGuard.guard(sqlTemplate, Map.of());

        // ② C4 关键：执行用 SQL 取自 stmt.toString()（JSqlParser 重新序列化），
        //    而非原始 sqlTemplate。这关闭了"原始 SQL 注释 / hint 不进 AST 但进 JDBC"的绕过。
        String deparsedSql = stmt.toString();

        // ③ C5 闭合：对 LIMIT/OFFSET 实参做运行时上限校验。
        //    必须先于 jdbcTemplate 调用，确保超限 SQL 永不抵达数据库。
        enforceBoundLimit(params);

        // ④ C3 闭合：把 :name 转 ?，收集有序参数值。
        SqlParamBinder.PreparedSql prepared = SqlParamBinder.convert(deparsedSql, params);

        // ⑤ 设置查询超时（秒）。setQueryTimeout 接受 int 秒。
        jdbcTemplate.setQueryTimeout(timeoutMs / 1000);

        // ⑥ 执行参数化查询（绝不字符串拼接）。
        return jdbcTemplate.queryForList(prepared.sql(), prepared.params().toArray());
    }

    /**
     * 运行时 LIMIT/OFFSET 上限校验（C5 闭合）。
     *
     * <p>T8 {@code SqlSafetyValidator} 的 {@code assertLimitWithinMax} 仅能校验
     * AST 中可静态识别为 {@link net.sf.jsqlparser.expression.LongValue} 的 LIMIT/OFFSET，
     * 对占位符 {@code LIMIT ?} 形式放行（交由本方法处理）。
     *
     * <p>策略：扫描 {@code params} 的 key，凡是名字（小写）包含 {@code limit}
     * 或 {@code offset} 的实参，若为 {@link Number} 且数值大于
     * {@link ScreenConstants#SQL_MAX_ROWS}，立即抛 {@link SqlSafetyException}。
     *
     * <p>命名约定按社区惯例：白名单 SQL 模板里 LIMIT/OFFSET 占位符通常命名为
     * {@code :limit} / {@code :offset} / {@code :pageSize} 等；这里用 {@code contains}
     * 而非精确匹配，覆盖诸如 {@code :maxLimit}、{@code :pageOffset} 等变体，同时
     * 接受少量误报（其他列名极少含这两个关键字）。
     */
    private void enforceBoundLimit(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase();
            if (!lower.contains("limit") && !lower.contains("offset")) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Number n) {
                long longValue = n.longValue();
                if (longValue > ScreenConstants.SQL_MAX_ROWS) {
                    throw new SqlSafetyException(
                        "参数 " + key + " 超过上限 " + ScreenConstants.SQL_MAX_ROWS
                            + "，当前: " + longValue);
                }
            }
        }
    }
}
