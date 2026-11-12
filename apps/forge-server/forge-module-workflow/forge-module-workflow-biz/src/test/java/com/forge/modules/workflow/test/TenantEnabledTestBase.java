package com.forge.modules.workflow.test;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * 开租户 E2E 基类（租户开闸前置 T10）：
 * 在 WorkflowIntegrationTestBase 之上以 properties 覆盖 forge.tenant.enable=true
 * （application-test.yml 默认 false），验证 TenantLine 插件/租户上下文全链行为；
 * fixture 经 @Sql 直连 DataSource 建表，不受租户拦截器影响。
 */
@SpringBootTest(classes = WorkflowTestApplication.class, properties = {
        "forge.tenant.enable=true"
})
public abstract class TenantEnabledTestBase extends WorkflowIntegrationTestBase {
}
