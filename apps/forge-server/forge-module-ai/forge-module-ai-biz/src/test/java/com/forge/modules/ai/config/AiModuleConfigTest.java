package com.forge.modules.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AI 客户端配置默认值单测：超时分级基线（AC2）
 */
class AiModuleConfigTest {

    @Test
    void 客户端默认值() {
        AiModuleConfig.Client client = new AiModuleConfig().getClient();
        assertEquals(5000, client.getConnectTimeout());
        assertEquals(60000, client.getReadTimeout()); // 代码级默认;yml 覆盖为 120000
        assertEquals(45000, client.getDecisionTimeout());
        assertEquals(60000, client.getSummaryTimeout());
        assertEquals(2, client.getFailoverParallelism());
        assertEquals(60000, client.getHealthCacheTtlMs());
    }
}
