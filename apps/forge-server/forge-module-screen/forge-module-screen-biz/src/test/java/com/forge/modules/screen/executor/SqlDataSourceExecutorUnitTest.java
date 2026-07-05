package com.forge.modules.screen.executor;

import com.forge.modules.screen.constant.ScreenConstants;
import com.forge.modules.screen.safety.SqlSafetyException;
import com.forge.modules.screen.safety.SqlSafetyGuard;
import com.forge.modules.screen.util.SqlParamBinder;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SqlDataSourceExecutor} 单元测试（TDD）。
 *
 * <p>覆盖三个边界：
 * <ul>
 *   <li>SqlParamBinder 命名参数转换正确（顺序、占位符、缺参 fail-closed）</li>
 *   <li>执行器在安全闸门失败时立即抛 {@link SqlSafetyException}，
 *       且不会调用 {@link JdbcTemplate}（防御 C4：使用 AST 重序列化的 SQL）</li>
 *   <li>执行器对 LIMIT/OFFSET 参数做运行时上限校验（C5 闭合）</li>
 * </ul>
 *
 * @author standadmin
 */
class SqlDataSourceExecutorUnitTest {

    /**
     * 命名参数按出现顺序转换为 JDBC 占位符，参数值列表保持同序。
     */
    @Test
    void sql_param_binder_converts_named_params() {
        SqlParamBinder.PreparedSql p = SqlParamBinder.convert(
            "SELECT id FROM sys_user WHERE status = :status AND user_name = :name LIMIT 1",
            Map.of("status", 0, "name", "admin"));
        assertThat(p.sql()).isEqualTo(
            "SELECT id FROM sys_user WHERE status = ? AND user_name = ? LIMIT 1");
        assertThat(p.params()).containsExactly(0, "admin");
    }

    /**
     * 引用了未提供的命名参数时立即 fail-closed。
     */
    @Test
    void sql_param_binder_throws_when_param_missing() {
        assertThatThrownBy(() -> SqlParamBinder.convert(
            "SELECT * FROM sys_user WHERE id = :id LIMIT 1", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id");
    }

    /**
     * 安全闸门抛出时执行器不再触达 JdbcTemplate。
     *
     * <p>断言三件事：
     * <ul>
     *   <li>异常类型为 {@link SqlSafetyException}</li>
     *   <li>JdbcTemplate.queryForList 永不被调用（mock jdbcTemplate 不会 NPE 即可）</li>
     *   <li>使用 deparsed SQL —— 通过 ArgumentCaptor 验证传入 guard 的是原始模板，
     *       而真正用于执行的 SQL 来自 {@code stmt.toString()}（由后续 integration 验证）。</li>
     * </ul>
     * 这里 jdbcTemplate 传 null，因为安全闸门必先抛出，jdbcTemplate 永不被调用；
     * 这也是 fail-closed 的实证：impl 必须先调用 guard 再访问 jdbcTemplate。
     */
    @Test
    void executor_throws_when_safety_check_fails() {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        doThrow(new SqlSafetyException("禁用 password 列"))
            .when(guard).guard(anyString(), anyMap());

        // jdbcTemplate 故意为 null：验证执行器在 guard 抛出后不会触碰 jdbcTemplate
        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, null);
        assertThatThrownBy(() -> executor.execute(
            "SELECT password FROM sys_user LIMIT 1", Map.of(), 5000))
            .isInstanceOf(SqlSafetyException.class);
    }

    /**
     * C5 闭合：当 SQL 用 {@code LIMIT ?}（占位符）规避静态 AST 上限校验时，
     * 运行时由 {@code enforceBoundLimit} 拦截超限的 LIMIT/OFFSET 实参。
     *
     * <p>构造一个安全闸门已通过的 mock 场景，params 携带 limit=10000，
     * 期望执行器在调到 jdbcTemplate 之前抛 {@link SqlSafetyException}。
     */
    @Test
    void executor_rejects_bound_limit_above_max() throws Exception {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        // 模拟 guard 通过：返回已 deparsed 的 Statement
        Statement stubStmt = CCJSqlParserUtil.parse(
            "SELECT id FROM sys_user LIMIT ?");
        when(guard.guard(anyString(), anyMap())).thenReturn(stubStmt);

        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, jdbcTemplate);

        assertThatThrownBy(() -> executor.execute(
            "SELECT id FROM sys_user LIMIT :limit",
            Map.of("limit", ScreenConstants.SQL_MAX_ROWS + 1),
            5000))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("limit");

        // 防御确认：jdbcTemplate 真正执行从未发生
        verify(jdbcTemplate, org.mockito.Mockito.never())
            .queryForList(anyString(), any(Object[].class));
    }

    /**
     * 正常路径：guard 通过 + LIMIT 在上限内 + deparsed SQL 用于执行。
     *
     * <p>关键 C4 断言：执行 JdbcTemplate 时使用的 SQL 是
     * {@code stmt.toString()}（JSqlParser 重新序列化）而非原始模板。
     * 这里通过让模板带一个会被 JSqlParser 归一化的差异（大小写/空白）来观察。
     */
    @Test
    void executor_uses_deparsed_sql_and_binds_params() throws Exception {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        // 模板用小写 limit；JSqlParser deparse 后会规范为 LIMIT ?
        Statement stubStmt = CCJSqlParserUtil.parse(
            "SELECT id FROM sys_user LIMIT ?");
        when(guard.guard(anyString(), anyMap())).thenReturn(stubStmt);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
            .thenReturn(List.of(Map.of("id", 1L)));

        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, jdbcTemplate);
        List<Map<String, Object>> rows = executor.execute(
            "select id from sys_user limit :limit",
            Map.of("limit", 50),
            5000);

        assertThat(rows).hasSize(1);
        // 关键：执行时传入的 SQL 来自 stmt.toString()（"SELECT id FROM sys_user LIMIT ?"），
        // 不是原始模板 "select id from sys_user limit :limit"。
        verify(jdbcTemplate).queryForList(
            eq("SELECT id FROM sys_user LIMIT ?"),
            any(Object[].class));
    }
}
