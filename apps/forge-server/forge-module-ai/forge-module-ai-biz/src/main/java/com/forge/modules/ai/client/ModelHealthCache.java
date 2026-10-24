package com.forge.modules.ai.client;

import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.entity.AiModelConfig;
import com.google.common.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * 模型健康负缓存：记录近期调用失败的模型（指纹→失败标记，expireAfterWrite TTL），
 * 零额外探测延迟——用于 failover 候选预筛，避免已知病态模型反复进入竞速白付 token/拖慢批次。
 * 成功即清除标记；读取异常 fail-open（等同无过滤，可用性优先）。
 */
@Slf4j
@Component
public class ModelHealthCache {

    private final Cache<String, Boolean> failures;

    public ModelHealthCache(LocalCaches localCaches, AiModuleConfig aiModuleConfig) {
        Integer ttl = aiModuleConfig.getClient().getHealthCacheTtlMs();
        long ttlMs = ttl == null || ttl <= 0 ? 60_000L : ttl;
        this.failures = localCaches.get("ai-model-health", b -> b
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMillis(ttlMs)));
    }

    /** 近期（TTL 内）是否失败过：true=应从 failover 候选中跳过 */
    public boolean recentlyFailed(AiModelConfig model) {
        try {
            return failures.getIfPresent(key(model)) == Boolean.TRUE;
        } catch (Exception e) {
            return false;
        }
    }

    /** 标记失败（TTL 到期自动失效，无需显式清除） */
    public void markFailed(AiModelConfig model) {
        try {
            failures.put(key(model), Boolean.TRUE);
            log.info("模型 [{}] 标记近期失败，TTL 内 failover 将跳过", model.getModelName());
        } catch (Exception e) {
            log.debug("健康失败标记写入异常: {}", e.getMessage());
        }
    }

    /** 调用成功清除失败标记 */
    public void markHealthy(AiModelConfig model) {
        try {
            failures.invalidate(key(model));
        } catch (Exception e) {
            log.debug("健康失败标记清除异常: {}", e.getMessage());
        }
    }

    /** 模型指纹：id + 配置更新时间（配置变更后视为新模型重新累计） */
    private String key(AiModelConfig model) {
        return Objects.toString(model.getId(), "") + "|" + Objects.toString(model.getUpdateTime(), "");
    }
}
