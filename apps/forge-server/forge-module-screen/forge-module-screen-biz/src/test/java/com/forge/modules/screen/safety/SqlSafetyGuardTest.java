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
import static org.mockito.Mockito.when;

/**
 * SqlSafetyGuard 单元测试（TDD）。
 *
 * <p>覆盖三类边界：合法 SELECT 放行、请求列含禁用列（password）被拒、表不在白名单被拒。
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
        wl.setColumnList("[\"id\",\"user_name\",\"status\"]");
        lenient().when(mapper.findByTable("forge_admin", "sys_user")).thenReturn(wl);
    }

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
}
