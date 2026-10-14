package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.ai.entity.AiSqlWhitelist;
import com.forge.modules.ai.service.AiSqlWhitelistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SQL 租户改写器单测：白名单校验 + 租户条件注入（覆盖验收标准 9 类场景）
 */
class SqlTenantRewriterTest {

    private AiSqlWhitelistService whitelistService;
    private TenantTableSchemaCache schemaCache;
    private TenantProperties tenantProperties;
    private SqlTenantRewriter rewriter;

    @BeforeEach
    void setUp() {
        whitelistService = mock(AiSqlWhitelistService.class);
        schemaCache = mock(TenantTableSchemaCache.class);
        tenantProperties = new TenantProperties();
        tenantProperties.setEnable(true);

        AiSqlWhitelist conversation = new AiSqlWhitelist();
        conversation.setTableName("ai_document");
        AiSqlWhitelist user = new AiSqlWhitelist();
        user.setTableName("sys_user");
        AiSqlWhitelist menu = new AiSqlWhitelist();
        menu.setTableName("sys_menu");
        when(whitelistService.getEnabledTables()).thenReturn(List.of(conversation, user, menu));

        // 租户表：ai_document、sys_user；sys_menu 为共享表
        when(schemaCache.getTenantTables()).thenReturn(Set.of("ai_document", "sys_user"));

        rewriter = new SqlTenantRewriter(whitelistService, schemaCache, tenantProperties);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void 单表有条件_追加租户过滤() {
        String result = rewriter.rewrite("SELECT * FROM ai_document WHERE id > 10");
        assertEquals("SELECT * FROM ai_document WHERE ai_document.tenant_id = 1 AND id > 10", result);
    }

    @Test
    void 单表无条件_补WHERE() {
        String result = rewriter.rewrite("SELECT * FROM ai_document");
        assertEquals("SELECT * FROM ai_document WHERE ai_document.tenant_id = 1", result);
    }

    @Test
    void 别名表_带别名注入() {
        String result = rewriter.rewrite("SELECT d.file_name FROM ai_document d WHERE d.id = 1");
        assertEquals("SELECT d.file_name FROM ai_document d WHERE d.tenant_id = 1 AND d.id = 1", result);
    }

    @Test
    void JOIN_主表注WHERE右表注ON() {
        String result = rewriter.rewrite(
                "SELECT d.file_name, u.username FROM ai_document d JOIN sys_user u ON d.user_id = u.id");
        assertTrue(result.contains("d.tenant_id = 1"));
        assertTrue(result.contains("u.tenant_id = 1"));
        assertTrue(result.contains("ON d.user_id = u.id AND u.tenant_id = 1"));
    }

    @Test
    void FROM子查询_内层注入() {
        String result = rewriter.rewrite(
                "SELECT * FROM (SELECT file_name FROM ai_document) t");
        assertTrue(result.contains("tenant_id = 1"));
    }

    @Test
    void WHERE_IN子查询_内层注入() {
        String result = rewriter.rewrite(
                "SELECT * FROM ai_document WHERE user_id IN (SELECT id FROM sys_user)");
        assertTrue(result.contains("ai_document.tenant_id = 1"));
        assertTrue(result.contains("sys_user.tenant_id = 1"));
    }

    @Test
    void 白名单外表_拒绝并列出可用表() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> rewriter.rewrite("SELECT * FROM sys_config"));
        assertTrue(e.getMessage().contains("sys_config"));
        assertTrue(e.getMessage().contains("ai_document"));
    }

    @Test
    void 共享表_不注入租户条件() {
        String result = rewriter.rewrite("SELECT * FROM sys_menu WHERE menu_name = 'x'");
        assertEquals("SELECT * FROM sys_menu WHERE menu_name = 'x'", result);
        assertFalse(result.contains("tenant_id"));
    }

    @Test
    void 租户开关关闭_白名单仍校验但不注入() {
        tenantProperties.setEnable(false);
        TenantContextHolder.clear();
        String result = rewriter.rewrite("SELECT * FROM sys_user WHERE id = 1");
        assertEquals("SELECT * FROM sys_user WHERE id = 1", result);
        assertThrows(BusinessException.class, () -> rewriter.rewrite("SELECT * FROM sys_config"));
    }

    @Test
    void 租户上下文缺失_拒绝执行() {
        TenantContextHolder.clear();
        assertThrows(BusinessException.class, () -> rewriter.rewrite("SELECT * FROM ai_document"));
    }

    @Test
    void CTE_内层真表注入_引用名跳过() {
        String result = rewriter.rewrite(
                "WITH recent AS (SELECT * FROM ai_document) SELECT * FROM recent");
        assertTrue(result.contains("ai_document.tenant_id = 1"));
    }

    @Test
    void 反引号表名_正常识别注入() {
        String result = rewriter.rewrite("SELECT * FROM `ai_document` WHERE id = 1");
        assertEquals("SELECT * FROM `ai_document` WHERE ai_document.tenant_id = 1 AND id = 1", result);
    }

    @Test
    void ORDER_BY子查询_内层注入() {
        String result = rewriter.rewrite(
                "SELECT file_name FROM ai_document ORDER BY (SELECT MAX(id) FROM sys_user)");
        assertTrue(result.contains("sys_user.tenant_id = 1"));
    }

    @Test
    void 解析失败_拒绝() {
        assertThrows(BusinessException.class, () -> rewriter.rewrite("SELECT * FRM ai_document"));
    }

    @Test
    void 非SELECT语句_拒绝() {
        assertThrows(BusinessException.class, () -> rewriter.rewrite("UPDATE ai_document SET file_name = 'x'"));
    }
}
