package com.forge.modules.screen.safety;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SqlSafetyValidator 12 种 SQL 安全用例单元测试（TDD）
 *
 * @author standadmin
 */
class SqlSafetyValidatorTest {

    SqlSafetyValidator validator;

    @BeforeEach
    void setup() {
        validator = new SqlSafetyValidator();
    }

    /**
     * 解析 SQL。若 JSqlParser 解析失败（例如 OUTFILE 等不被支持的语法），
     * 同样视为不安全 SQL，直接抛出 {@link SqlSafetyException}，
     * 这样在 production pipeline 中"无法解析的 SQL"会与"违反规则的 SQL"
     * 一并落入统一拒绝路径（解析在 validator 之前发生）。
     */
    private Statement parse(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new SqlSafetyException("SQL 解析失败: " + e.getMessage());
        }
    }

    @Test
    void reject_union_injection() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT * FROM sys_user UNION SELECT password FROM sys_user")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("UNION");
    }

    @Test
    void reject_comment_injection() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT * FROM sys_user WHERE 1=1 -- ; DROP TABLE sys_user")))
            .isInstanceOf(SqlSafetyException.class);
    }

    @Test
    void reject_system_table() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT * FROM information_schema.tables LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("system");
    }

    @Test
    void reject_dangerous_function_load_file() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT LOAD_FILE('/etc/passwd') FROM sys_user LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("function");
    }

    @Test
    void reject_no_limit() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT id FROM sys_user")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("LIMIT");
    }

    @Test
    void reject_limit_too_large() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT id FROM sys_user LIMIT 100000")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("1000");
    }

    @Test
    void reject_non_select_delete() {
        assertThatThrownBy(() -> validator.validate(parse(
            "DELETE FROM sys_user WHERE 1=1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("SELECT");
    }

    @Test
    void reject_non_select_drop() {
        assertThatThrownBy(() -> validator.validate(parse(
            "DROP TABLE sys_user")))
            .isInstanceOf(SqlSafetyException.class);
    }

    @Test
    void reject_into_outfile() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT * INTO OUTFILE '/tmp/x' FROM sys_user LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class);
    }

    @Test
    void reject_sleep_function() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT SLEEP(100000) FROM sys_user LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("function");
    }

    @Test
    void reject_information_schema_aliased() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT u.id FROM sys_user u, information_schema.tables t LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("system");
    }

    @Test
    void accept_valid_select() {
        assertThatCode(() -> validator.validate(parse(
            "SELECT id, user_name, status FROM sys_user WHERE status = 0 LIMIT 100")))
            .doesNotThrowAnyException();
    }

    // ============ Task 8 review regression tests (C1 / C2 / C3 / C6) ============

    /**
     * C1 — 反引号引用的系统表不应绕过 assertNoSystemTable。
     * TablesNamesFinder 返回的字符串保留反引号，必须先剥离再匹配。
     */
    @Test
    void reject_backtick_quoted_system_table() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT * FROM `information_schema`.`tables` LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("system");
    }

    /**
     * C2 — SELECT 子项中的标量子查询隐藏 SLEEP，必须递归扫描到。
     */
    @Test
    void reject_sleep_in_scalar_subquery_select() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT (SELECT SLEEP(5)) FROM sys_user LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("function");
    }

    /**
     * C2 — WHERE 子句中的标量子查询隐藏 BENCHMARK，必须递归扫描到。
     */
    @Test
    void reject_function_in_scalar_subquery_where() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT id FROM sys_user WHERE id IN (SELECT BENCHMARK(1000000, 1)) LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("function");
    }

    /**
     * C3 — OFFSET 形式的限制：rowCount=1 在范围内，但 offset=999999 远超上限。
     */
    @Test
    void reject_offset_bypass() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT id FROM sys_user LIMIT 1 OFFSET 999999")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("1000");
    }

    /**
     * C3 — MySQL 逗号形式 LIMIT：JSqlParser 解析为 rowCount=1, offset=999999。
     */
    @Test
    void reject_comma_form_limit_bypass() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT id FROM sys_user LIMIT 999999, 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("1000");
    }

    /**
     * C3 — LIMIT 后是表达式（Addition），按 fail-closed 原则拒绝。
     */
    @Test
    void reject_expression_in_limit() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT id FROM sys_user LIMIT 5 + 5")))
            .isInstanceOf(SqlSafetyException.class);
    }

    // ============ 额外防御性测试（reviewer probe variants） ============

    /**
     * C1 防御加深 — 双引号引用的系统表（PostgreSQL/标准 SQL 写法）也应被拦截。
     * {@link SqlSafetyValidator#normalizeIdentifier} 同时剥离双引号。
     */
    @Test
    void reject_double_quoted_system_table() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT * FROM \"information_schema\".\"tables\" LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("system");
    }

    /**
     * C2 防御加深 — 标量子查询出现在 WHERE 布尔表达式里（独立 = 比较形式），
     * 不止 IN 子查询。来自 reviewer report 的原 repro 之一。
     */
    @Test
    void reject_sleep_in_where_boolean_subquery() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT id FROM sys_user WHERE id = 1 OR (SELECT SLEEP(5)) = 1 LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("function");
    }

    /**
     * C6 — pg_sleep 等跨方言延时函数即使在 MySQL 部署下也拒绝（防御性 listing）。
     */
    @Test
    void reject_pg_sleep_function() {
        assertThatThrownBy(() -> validator.validate(parse(
            "SELECT pg_sleep(5) FROM sys_user LIMIT 1")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("function");
    }
}
