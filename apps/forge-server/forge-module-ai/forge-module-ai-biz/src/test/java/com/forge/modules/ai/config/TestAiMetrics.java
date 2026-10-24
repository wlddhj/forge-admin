package com.forge.modules.ai.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 测试用 no-op 指标实例（registry 缺失，全量方法安全空转）
 */
public final class TestAiMetrics {

    private TestAiMetrics() {
    }

    public static AiMetrics noop() {
        return new AiMetrics(new ObjectProvider<>() {
            @Override
            public MeterRegistry getIfAvailable() {
                return null;
            }
        });
    }
}
