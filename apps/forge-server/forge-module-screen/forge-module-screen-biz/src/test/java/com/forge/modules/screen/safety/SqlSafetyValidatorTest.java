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
}
