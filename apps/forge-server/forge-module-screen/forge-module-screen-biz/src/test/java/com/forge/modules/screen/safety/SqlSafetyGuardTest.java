package com.forge.modules.screen.safety;

import com.forge.modules.screen.entity.SysScreenSqlWhitelist;
import com.forge.modules.screen.mapper.SysScreenSqlWhitelistMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * SqlSafetyGuard 单元测试（TDD）。
 *
 * <p>覆盖四组边界：合法 SELECT 放行、请求列含禁用列（password）被拒、表不在白名单被拒；
 * 闸A 注释硬拒（块注释 / MySQL hint / 行注释 / 字符串字面量误伤）；闸B 全树表/列白名单
 * （JOIN 他表 / JOIN 白名单表 / WHERE 白名单外列 / ORDER BY 白名单外列 / 别名解析）；
 * 闸C LIMIT/OFFSET 命名参数收集。
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
class SqlSafetyGuardTest {

    @Mock
    SysScreenSqlWhitelistMapper mapper;

    SqlSafetyGuard guard;

    @BeforeEach
    void setup() {
        SqlSafetyValidator validator = new SqlSafetyValidator();
        WhitelistService whitelist = new WhitelistService(mapper);
        guard = new SqlSafetyGuard(validator, whitelist);

        SysScreenSqlWhitelist wl = new SysScreenSqlWhitelist();
        wl.setSchemaName("forge_admin");
        wl.setTableName("sys_user");
        wl.setColumnList("[\"id\",\"user_name\",\"status\",\"dept_id\"]");
        lenient().when(mapper.findByTable("forge_admin", "sys_user")).thenReturn(wl);

        SysScreenSqlWhitelist dept = new SysScreenSqlWhitelist();
        dept.setSchemaName("forge_admin");
        dept.setTableName("sys_dept");
        dept.setColumnList("[\"id\",\"dept_name\"]");
        lenient().when(mapper.findByTable("forge_admin", "sys_dept")).thenReturn(dept);
    }

    // ============ 既有三边界 ============

    @Test
    void guard_accepts_safe_select() {
        String sql = "SELECT id, user_name FROM sys_user WHERE status = 0 LIMIT 100";
        Map<String, Set<String>> requested = Map.of("sys_user", Set.of("id", "user_name", "status"));
        assertThatCode(() -> guard.guard(sql, requested)).doesNotThrowAnyException();
    }

    @Test
    void guard_rejects_when_password_column_requested() {
        String sql = "SELECT id, password FROM sys_user LIMIT 1";
        Map<String, Set<String>> requested = Map.of("sys_user", Set.of("id", "password"));
        assertThatThrownBy(() -> guard.guard(sql, requested))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("password");
    }

    @Test
    void guard_rejects_when_table_not_in_whitelist() {
        String sql = "SELECT * FROM sys_forbidden LIMIT 1";
        Map<String, Set<String>> requested = Map.of("sys_forbidden", Set.of());
        assertThatThrownBy(() -> guard.guard(sql, requested))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("不在白名单");
    }

    // ============ 闸A：模板注释硬拒（fail-closed） ============

    /**
     * 块注释 {@code /* ... *&#47;}：JSqlParser 解析时会剥离、deparse 也不含注释，
     * 但闸A 在解析前对原始字符串硬拒，不依赖解析器的剥离行为。
     */
    @Test
    void guard_rejects_block_comment_template() {
        assertThatThrownBy(() -> guard.guard(
            "SELECT id FROM sys_user LIMIT 1 /* trailing */", Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("注释");
    }

    /**
     * MySQL hint 形式 {@code /*! ... *&#47;}：同样在解析前硬拒。
     */
    @Test
    void guard_rejects_mysql_hint_comment_template() {
        assertThatThrownBy(() -> guard.guard(
            "SELECT /*! BIGINT */ id FROM sys_user LIMIT 1", Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("注释");
    }

    /**
     * 行注释 {@code --}：硬拒。
     */
    @Test
    void guard_rejects_line_comment_template() {
        assertThatThrownBy(() -> guard.guard(
            "SELECT id FROM sys_user LIMIT 1 -- tail", Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("注释");
    }

    /**
     * 已确认接受的误伤：字符串字面量中的 {@code --}（如 {@code 'a--b'}）也会被硬拒。
     * fail-closed 优先，属预期行为（设计已列明）。
     */
    @Test
    void guard_rejects_dash_dash_inside_string_literal_collaterally() {
        assertThatThrownBy(() -> guard.guard(
            "SELECT id FROM sys_user WHERE user_name = 'a--b' LIMIT 1", Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("注释");
    }

    // ============ 闸B：全树表/列白名单 ============

    /**
     * JOIN 的右表不在白名单 → 全量表提取（TablesNamesFinder）后 fail-closed。
     */
    @Test
    void guard_rejects_join_to_table_outside_whitelist() {
        String sql = "SELECT sys_user.id FROM sys_user "
            + "JOIN sys_forbidden ON sys_user.dept_id = sys_forbidden.id LIMIT 10";
        assertThatThrownBy(() -> guard.guard(sql, Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("sys_forbidden");
    }

    /**
     * JOIN 的两表均在白名单，且 ON / WHERE 引用的列均命中列白名单 → 放行。
     */
    @Test
    void guard_accepts_join_between_whitelisted_tables() {
        String sql = "SELECT sys_user.id, sys_dept.dept_name FROM sys_user "
            + "JOIN sys_dept ON sys_user.dept_id = sys_dept.id "
            + "WHERE sys_user.status = 0 LIMIT 10";
        assertThatCode(() -> guard.guard(sql, Map.of())).doesNotThrowAnyException();
    }

    /**
     * WHERE 中的列不在主表白名单 → 拒绝（此前 WHERE 列完全绕过列白名单）。
     */
    @Test
    void guard_rejects_where_column_outside_whitelist() {
        String sql = "SELECT id FROM sys_user WHERE password = 'x' LIMIT 1";
        assertThatThrownBy(() -> guard.guard(sql, Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("password");
    }

    /**
     * ORDER BY 中的列不在主表白名单 → 拒绝。
     */
    @Test
    void guard_rejects_order_by_column_outside_whitelist() {
        String sql = "SELECT id FROM sys_user ORDER BY password LIMIT 1";
        assertThatThrownBy(() -> guard.guard(sql, Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("password");
    }

    /**
     * 别名限定的 WHERE 列：按 别名 -> 真实表 映射解析到 sys_user 后正常放行，
     * 不因限定名是别名而非表名被误拒。
     */
    @Test
    void guard_accepts_alias_qualified_where_columns() {
        String sql = "SELECT id FROM sys_user u WHERE u.status = 0 LIMIT 10";
        assertThatCode(() -> guard.guard(sql, Map.of())).doesNotThrowAnyException();
    }

    /**
     * SELECT 项中带限定符的白名单外列（阶段四审查补）：u.password 不经纯标识符提取路径，
     * 必须由全树 visitor 覆盖，否则 SELECT 面可绕过列白名单。
     */
    @Test
    void guard_rejects_qualified_select_column_outside_whitelist() {
        String sql = "SELECT u.password FROM sys_user u LIMIT 1";
        assertThatThrownBy(() -> guard.guard(sql, Map.of()))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("password");
    }

    /**
     * SELECT 项中带限定符的白名单内列正常放行（防止上一条修复误伤）。
     */
    @Test
    void guard_accepts_qualified_select_column_in_whitelist() {
        String sql = "SELECT u.user_name, d.dept_name FROM sys_user u JOIN sys_dept d ON u.dept_id = d.id LIMIT 10";
        assertThatCode(() -> guard.guard(sql, Map.of())).doesNotThrowAnyException();
    }

    // ============ 闸C：LIMIT/OFFSET 命名参数收集 ============

    /**
     * {@code LIMIT :n}：静态闸放行占位符，参数名进入 limitParamNames
     * （供 T11 运行时精确匹配校验）。
     */
    @Test
    void guard_collects_named_limit_param_names() {
        GuardResult result = guard.guard("SELECT id FROM sys_user LIMIT :n", Map.of());
        assertThat(result.statement()).isNotNull();
        assertThat(result.limitParamNames()).containsExactly("n");
    }

    /**
     * {@code LIMIT 10 OFFSET :off}：独立 OFFSET 子句的命名参数同样收集。
     */
    @Test
    void guard_collects_named_offset_param_names() {
        GuardResult result = guard.guard(
            "SELECT id FROM sys_user LIMIT 10 OFFSET :off", Map.of());
        assertThat(result.limitParamNames()).containsExactly("off");
    }
}
