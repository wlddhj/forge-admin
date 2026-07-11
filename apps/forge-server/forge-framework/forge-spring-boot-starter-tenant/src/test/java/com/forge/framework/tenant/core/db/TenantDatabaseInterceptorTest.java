package com.forge.framework.tenant.core.db;

import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TenantDatabaseInterceptorTest {

    private TenantDatabaseInterceptor interceptor;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setIgnoreTables(Set.of("sys_menu", "sys_dict_type"));
        interceptor = new TenantDatabaseInterceptor(props);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getTenantId_returnsCurrentTenantId() {
        TenantContextHolder.setTenantId(42L);
        Expression expr = interceptor.getTenantId();
        assertNotNull(expr);
        assertEquals("42", expr.toString());
    }

    @Test
    void ignoreTable_returnsTrueForIgnoreTable() {
        assertTrue(interceptor.ignoreTable("sys_menu"));
        assertTrue(interceptor.ignoreTable("SYS_MENU"));  // 大小写不敏感
    }

    @Test
    void ignoreTable_returnsFalseForNormalTable() {
        assertFalse(interceptor.ignoreTable("sys_user"));
    }

    @Test
    void ignoreTable_returnsTrueWhenGlobalIgnore() {
        TenantContextHolder.setIgnore(true);
        assertTrue(interceptor.ignoreTable("sys_user"));
    }

    @Test
    void getRequiredTenantId_throwsWhenMissing() {
        assertThrows(NullPointerException.class, TenantContextHolder::getRequiredTenantId);
    }
}