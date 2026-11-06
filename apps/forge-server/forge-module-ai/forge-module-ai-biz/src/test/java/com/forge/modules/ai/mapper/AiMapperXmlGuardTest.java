package com.forge.modules.ai.mapper;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.forge.modules.ai.agent.lifecycle.AiDataCleanupMapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 模块 mapper XML 守卫。mapper 单测均为 mock、不执行 SQL，以下两类问题只会在运行时暴露，故前移到 mvn test：
 * <ol>
 *   <li>upsert 须能被租户插件 TenantLineInnerInterceptor（JSqlParser，项目锁 4.9）解析并注入 tenant_id。
 *       租户插件默认启用（matchIfMissing=true）且重新解析每条 INSERT；MySQL 8.0.20 弃用 VALUES() 后的替代写法
 *       「VALUES(...) AS new 行别名」需 JSqlParser 5.0+，误改即 TENANT_ENABLE=true 时运行时报错</li>
 *   <li>AiDataCleanupMapper.xml 经 OGNL 按名引用接口常量 BATCH_LIMIT，常量改名不会编译报错，
 *       只会在清理任务运行时失败</li>
 * </ol>
 */
class AiMapperXmlGuardTest {

    private static final Configuration CONFIGURATION = new Configuration();

    private final TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(new TenantLineHandler() {
        @Override
        public Expression getTenantId() {
            return new LongValue(1);
        }
    });

    @BeforeAll
    static void loadMapperXml() throws Exception {
        for (String resource : List.of("mapper/ai/AiApiUsageMapper.xml", "mapper/ai/AiAgentCheckpointMapper.xml",
                "mapper/ai/AiDataCleanupMapper.xml")) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, CONFIGURATION, resource, CONFIGURATION.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void 用量upsert可被租户插件解析并注入租户列() {
        String sql = tenantRewrite(AiApiUsageMapper.class.getName() + ".upsertIncrement");
        assertTrue(sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE"), sql);
        assertTrue(sql.toLowerCase().contains("tenant_id"), sql);
    }

    @Test
    void 检查点upsert可被租户插件解析并注入租户列() {
        String sql = tenantRewrite(AiAgentCheckpointMapper.class.getName() + ".upsert");
        assertTrue(sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE"), sql);
        assertTrue(sql.toLowerCase().contains("tenant_id"), sql);
    }

    @Test
    void 清理选id语句按BATCH_LIMIT常量渲染分批上限() {
        String sql = CONFIGURATION.getMappedStatement(AiDataCleanupMapper.class.getName() + ".selectExpiredRunIds")
                .getBoundSql(Map.of("cutoff", LocalDateTime.now())).getSql();
        assertTrue(sql.contains("LIMIT " + AiDataCleanupMapper.BATCH_LIMIT), sql);
    }

    /** XML 解析出的 BoundSql（静态 SQL，与参数值无关）交给租户插件改写，模拟运行时拦截 */
    private String tenantRewrite(String statementId) {
        String sql = CONFIGURATION.getMappedStatement(statementId).getBoundSql(null).getSql();
        return tenant.parserMulti(sql, null);
    }
}
