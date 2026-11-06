package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * upsert SQL 守卫：必须能被租户插件 TenantLineInnerInterceptor（JSqlParser，项目锁 4.9）解析并注入 tenant_id。
 * AI 模块 mapper 单测均为 mock 不执行 SQL，租户插件默认启用（matchIfMissing=true）且对 @Insert 重新解析，
 * 语法不兼容（如 MySQL 8.0.20 弃用 VALUES() 的替代「VALUES(...) AS new 行别名」需 JSqlParser 5.0+）
 * 在单测阶段无法暴露——本守卫把解析阶段前移到 mvn test，防止误"升级"写法导致 TENANT_ENABLE=true 运行时炸。
 */
class AiUpsertSqlParseGuardTest {

    private final TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(new TenantLineHandler() {
        @Override
        public Expression getTenantId() {
            return new LongValue(1);
        }
    });

    @Test
    void 用量upsert可被租户插件解析并注入租户列() {
        String sql = rewrite(AiApiUsageMapper.class, "upsertIncrement");
        assertTrue(sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE"), sql);
        assertTrue(sql.toLowerCase().contains("tenant_id"), sql);
    }

    @Test
    void 检查点upsert可被租户插件解析并注入租户列() {
        String sql = rewrite(AiAgentCheckpointMapper.class, "upsert");
        assertTrue(sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE"), sql);
        assertTrue(sql.toLowerCase().contains("tenant_id"), sql);
    }

    /** 取 @Insert 原文，MyBatis 占位符换成 JDBC ? 后交给租户插件改写（模拟 BoundSql 阶段） */
    private String rewrite(Class<?> mapper, String method) {
        Method m = Arrays.stream(mapper.getMethods())
                .filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
        String sql = String.join("", m.getAnnotation(Insert.class).value()).replaceAll("#\\{[^}]+}", "?");
        return tenant.parserMulti(sql, null);
    }
}
