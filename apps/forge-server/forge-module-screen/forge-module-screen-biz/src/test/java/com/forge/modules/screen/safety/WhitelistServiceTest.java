package com.forge.modules.screen.safety;

import com.forge.modules.screen.entity.SysScreenSqlWhitelist;
import com.forge.modules.screen.mapper.SysScreenSqlWhitelistMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * WhitelistService 单元测试（TDD）。
 *
 * <p>覆盖 4 个核心场景：表在白名单、表不在白名单、列全部命中白名单、列含禁用字段。
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
class WhitelistServiceTest {

    @Mock
    SysScreenSqlWhitelistMapper mapper;

    @InjectMocks
    WhitelistService service;

    @BeforeEach
    void setup() {
        SysScreenSqlWhitelist wl = new SysScreenSqlWhitelist();
        wl.setSchemaName("forge_admin");
        wl.setTableName("sys_user");
        wl.setColumnList("[\"id\",\"user_name\",\"status\"]");
        lenient().when(mapper.findByTable("forge_admin", "sys_user")).thenReturn(wl);
        lenient().when(mapper.findByTable("forge_admin", "sys_forbidden")).thenReturn(null);
    }

    @Test
    void checkTableAllowed_returns_whitelist_when_present() {
        SysScreenSqlWhitelist wl = service.checkTableAllowed("forge_admin", "sys_user");
        assertThat(wl).isNotNull();
    }

    @Test
    void checkTableAllowed_returns_null_when_absent() {
        assertThat(service.checkTableAllowed("forge_admin", "sys_forbidden")).isNull();
    }

    @Test
    void checkColumnsAllowed_passes_when_all_in_whitelist() {
        service.checkColumnsAllowed("forge_admin", "sys_user",
            Set.of("id", "user_name", "status"));
    }

    @Test
    void checkColumnsAllowed_throws_when_password_requested() {
        assertThatThrownBy(() -> service.checkColumnsAllowed("forge_admin", "sys_user",
            Set.of("id", "password")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("password");
    }

    /**
     * I6 修复：column_list 为空时 fail-closed，拒绝所有请求列（最小授权）。
     */
    @Test
    void checkColumnsAllowed_fails_closed_when_column_list_is_empty() {
        SysScreenSqlWhitelist wl = new SysScreenSqlWhitelist();
        wl.setSchemaName("forge_admin");
        wl.setTableName("sys_dept");
        wl.setColumnList("  ");  // 空白
        lenient().when(mapper.findByTable("forge_admin", "sys_dept")).thenReturn(wl);

        assertThatThrownBy(() -> service.checkColumnsAllowed("forge_admin", "sys_dept",
            Set.of("id")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("fail-closed");
    }

    /**
     * I6 修复：column_list 为 null 同样 fail-closed。
     */
    @Test
    void checkColumnsAllowed_fails_closed_when_column_list_is_null() {
        SysScreenSqlWhitelist wl = new SysScreenSqlWhitelist();
        wl.setSchemaName("forge_admin");
        wl.setTableName("sys_role");
        wl.setColumnList(null);
        lenient().when(mapper.findByTable("forge_admin", "sys_role")).thenReturn(wl);

        assertThatThrownBy(() -> service.checkColumnsAllowed("forge_admin", "sys_role",
            Set.of("id")))
            .isInstanceOf(SqlSafetyException.class)
            .hasMessageContaining("fail-closed");
    }
}
