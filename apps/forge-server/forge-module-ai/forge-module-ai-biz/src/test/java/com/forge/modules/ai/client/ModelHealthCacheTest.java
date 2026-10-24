package com.forge.modules.ai.client;

import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.entity.AiModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型健康负缓存单测：标记/跳过/成功清除/TTL 过期/指纹隔离（AC2）
 */
class ModelHealthCacheTest {

    private ModelHealthCache cache;
    private final AiModelConfig model = model(1L);

    @BeforeEach
    void setUp() {
        AiModuleConfig config = new AiModuleConfig();
        config.getClient().setHealthCacheTtlMs(200); // 短 TTL 便于验证过期
        cache = new ModelHealthCache(new LocalCaches(), config);
    }

    @Test
    void 标记失败后TTL内跳过_成功后恢复() {
        assertFalse(cache.recentlyFailed(model));
        cache.markFailed(model);
        assertTrue(cache.recentlyFailed(model));
        cache.markHealthy(model);
        assertFalse(cache.recentlyFailed(model));
    }

    @Test
    void TTL过期自动恢复() throws InterruptedException {
        cache.markFailed(model);
        assertTrue(cache.recentlyFailed(model));
        Thread.sleep(350);
        assertFalse(cache.recentlyFailed(model));
    }

    @Test
    void 指纹隔离_配置变更视为新模型() {
        AiModelConfig updated = model(1L);
        updated.setUpdateTime(java.time.LocalDateTime.now());
        cache.markFailed(model);
        assertTrue(cache.recentlyFailed(model));
        assertFalse(cache.recentlyFailed(updated));
        assertFalse(cache.recentlyFailed(model(2L)));
    }

    private AiModelConfig model(Long id) {
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setModelName("m" + id);
        return config;
    }
}
