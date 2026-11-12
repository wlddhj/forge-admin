package com.forge.modules.screen.executor;

import com.forge.modules.screen.constant.ScreenConstants;
import com.forge.modules.screen.mapper.DynamicSqlMapper;
import com.forge.modules.screen.safety.GuardResult;
import com.forge.modules.screen.safety.SqlSafetyException;
import com.forge.modules.screen.safety.SqlSafetyGuard;
import com.forge.modules.screen.util.SqlParamBinder;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SqlDataSourceExecutor} 单元测试。
 *
 * <p>覆盖三个边界：
 * <ul>
 *   <li>SqlParamBinder 命名参数转换正确（顺序、占位符、缺参 fail-closed）</li>
 *   <li>执行器在安全闸门失败时立即抛 {@link SqlSafetyException}，
 *       且不会调用 {@link DynamicSqlMapper}（防御 C4：使用 AST 重序列化的 SQL）</li>
 *   <li>执行器对 LIMIT/OFFSET 参数做运行时上限校验（C5 闭合）</li>
 * </ul>
 *
 * <p>C3 修复后执行路径从 {@code JdbcTemplate} 切换到 {@link DynamicSqlMapper}，
 * 以走 MyBatis Plus 拦截器链（含 {@code DataPermissionInterceptor}）。
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
     * {@code ?} → {@code #{pi}} 转换：用于走 MyBatis ${sql} 注入。
     */
    @Test
    void sql_param_binder_to_mybatis_placeholders() {
        SqlParamBinder.PreparedSql p = SqlParamBinder.convert(
            "SELECT id FROM sys_user WHERE status = :status AND user_name = :name LIMIT :limit",
            Map.of("status", 0, "name", "admin", "limit", 50));
        String mybatis = SqlParamBinder.toMybatisPlaceholders(p);
        assertThat(mybatis).isEqualTo(
            "SELECT id FROM sys_user WHERE status = #{p0} AND user_name = #{p1} LIMIT #{p2}");
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
     * 安全闸门抛出时执行器不再触达 DynamicSqlMapper。
     */
    @Test
    void executor_throws_when_safety_check_fails() {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        doThrow(new SqlSafetyException("禁用 password 列"))
            .when(guard).guard(anyString(), anyMap());

        // mapper 故意为 null：验证执行器在 guard 抛出后不会触碰 mapper
        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, null);
        assertThatThrownBy(() -> executor.execute(
            "SELECT password FROM sys_user LIMIT 1", Map.of(), 5000))
            .isInstanceOf(SqlSafetyException.class);
    }

    /**
     * C5 闭合：当 SQL 用 {@code LIMIT ?}（占位符）规避静态 AST 上限校验时，
     * 运行时由 {@code enforceBoundLimit} 按 limitParamNames 精确匹配拦截超限实参。
     */
    @Test
    void executor_rejects_bound_limit_above_max() throws Exception {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        DynamicSqlMapper mapper = mock(DynamicSqlMapper.class);
        Statement stubStmt = CCJSqlParserUtil.parse(
            "SELECT id FROM sys_user LIMIT ?");
        when(guard.guard(anyString(), anyMap()))
            .thenReturn(new GuardResult(stubStmt, Set.of("limit")));

        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, mapper);

        assertThatThrownBy(() -> executor.execute(
            "SELECT id FROM sys_user LIMIT :limit",
            Map.of("limit", ScreenConstants.SQL_MAX_ROWS + 1),
            5000))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("limit");

        // 防御确认：mapper 真正执行从未发生
        verify(mapper, never())
            .executeDynamicSql(anyString(), anyMap());
    }

    /**
     * 闸C 精确匹配：LIMIT 参数名不含 limit/offset 字样（如 {@code :n}）时，
     * 只要它在 limitParamNames 中且实参超上限即被拒（旧版"名字包含"匹配会漏掉）。
     */
    @Test
    void executor_rejects_named_limit_param_without_limit_keyword() throws Exception {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        DynamicSqlMapper mapper = mock(DynamicSqlMapper.class);
        Statement stubStmt = CCJSqlParserUtil.parse(
            "SELECT id FROM sys_user LIMIT ?");
        when(guard.guard(anyString(), anyMap()))
            .thenReturn(new GuardResult(stubStmt, Set.of("n")));

        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, mapper);

        assertThatThrownBy(() -> executor.execute(
            "SELECT id FROM sys_user LIMIT :n",
            Map.of("n", ScreenConstants.SQL_MAX_ROWS + 1),
            5000))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("n");

        verify(mapper, never()).executeDynamicSql(anyString(), anyMap());
    }

    /**
     * 闸C 精确匹配语义变化：名字含 limit 字样但未出现在 LIMIT 子句的业务参数
     * 不再被误伤（不在 limitParamNames 中即不做上限校验）。
     */
    @Test
    void executor_skips_limit_check_for_param_not_in_limit_clause() throws Exception {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        DynamicSqlMapper mapper = mock(DynamicSqlMapper.class);
        Statement stubStmt = CCJSqlParserUtil.parse(
            "SELECT id FROM sys_user WHERE status = ? LIMIT 1");
        when(guard.guard(anyString(), anyMap()))
            .thenReturn(new GuardResult(stubStmt, Set.of()));
        when(mapper.executeDynamicSql(anyString(), anyMap()))
            .thenReturn(List.of(Map.of("id", 1L)));

        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, mapper);
        List<Map<String, Object>> rows = executor.execute(
            "SELECT id FROM sys_user WHERE status = :limitStatus LIMIT 1",
            Map.of("limitStatus", 5000),
            5000);

        // 名字含 limit 但非 LIMIT 子句参数：正常执行，不再误拒
        assertThat(rows).hasSize(1);
        verify(mapper).executeDynamicSql(anyString(), anyMap());
    }

    /**
     * setMaxRows 等价兜底：无论 SQL 形态，返回行数被钳制在
     * {@link ScreenConstants#SQL_MAX_ROWS} 内（MyBatis 路径拿不到 Statement 句柄，
     * 以结果截断实现同等防护）。
     */
    @Test
    void executor_caps_result_rows_to_max_rows() throws Exception {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        DynamicSqlMapper mapper = mock(DynamicSqlMapper.class);
        Statement stubStmt = CCJSqlParserUtil.parse(
            "SELECT id FROM sys_user LIMIT 1");
        when(guard.guard(anyString(), anyMap()))
            .thenReturn(new GuardResult(stubStmt, Set.of()));
        List<Map<String, Object>> oversized = IntStream.rangeClosed(1, ScreenConstants.SQL_MAX_ROWS + 5)
            .mapToObj(i -> Map.<String, Object>of("id", (long) i))
            .toList();
        when(mapper.executeDynamicSql(anyString(), anyMap())).thenReturn(oversized);

        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, mapper);
        List<Map<String, Object>> rows = executor.execute(
            "SELECT id FROM sys_user LIMIT 1", Map.of(), 5000);

        assertThat(rows).hasSize(ScreenConstants.SQL_MAX_ROWS);
    }

    /**
     * 正常路径：guard 通过 + LIMIT 在上限内 + 原始模板通过 mapper 执行
     * （C3 修复后由 DynamicSqlMapper 接管，不再走 JdbcTemplate）。
     *
     * <p>C4 防御仍由 guard 提供（解析 + AST 校验）；参数绑定基于原始模板，
     * 避免 JSqlParser deparse 把 {@code :name} 规范化为 {@code ?} 导致漏绑定。
     */
    @Test
    void executor_routes_through_dynamic_sql_mapper() throws Exception {
        SqlSafetyGuard guard = mock(SqlSafetyGuard.class);
        DynamicSqlMapper mapper = mock(DynamicSqlMapper.class);
        Statement stubStmt = CCJSqlParserUtil.parse(
            "SELECT id FROM sys_user LIMIT 1");
        when(guard.guard(anyString(), anyMap()))
            .thenReturn(new GuardResult(stubStmt, Set.of()));
        when(mapper.executeDynamicSql(anyString(), anyMap()))
            .thenReturn(List.of(Map.of("id", 1L)));

        SqlDataSourceExecutor executor = new SqlDataSourceExecutor(guard, mapper);
        List<Map<String, Object>> rows = executor.execute(
            "select id from sys_user limit :limit",
            Map.of("limit", 50),
            5000);

        assertThat(rows).hasSize(1);
        // 关键：传给 mapper 的 SQL 来自原始模板的 :name → #{p0} 转换；
        // deparsed SQL 仅作 AST 验证用途，不再进入执行路径。
        verify(mapper).executeDynamicSql(
            eq("select id from sys_user limit #{p0}"),
            eq(Map.of("p0", 50)));
    }
}
