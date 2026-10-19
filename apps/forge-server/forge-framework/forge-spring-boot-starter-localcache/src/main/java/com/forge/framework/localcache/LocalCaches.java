package com.forge.framework.localcache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * 本地缓存统一入口：按名创建/复用 Guava Cache。
 * 不做配置化——每个缓存的规格（容量/TTL/统计等）由调用方在实现代码中写死传入，
 * 与 Spring Cache（@Cacheable/Redis）体系互不干扰。
 *
 * 用法（建议在 Bean 构造器中取一次并持有字段引用）：
 * <pre>
 * Cache&lt;Long, Client&gt; clients = localCaches.get("file-storage-minio",
 *         b -&gt; b.maximumSize(64).expireAfterAccess(Duration.ofMinutes(30)));
 * </pre>
 */
public class LocalCaches {

    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();

    /**
     * 获取（或首次创建）指定名称的缓存。
     * 同名复用同一实例，后续调用传入的 spec 会被忽略——规格以首次创建为准，
     * 因此各业务应在唯一入口处定义规格。
     *
     * @param name 缓存名（全局唯一，建议「业务域-用途」风格）
     * @param spec 缓存规格定制器，入参为 CacheBuilder.newBuilder()
     * @param <K>  键类型
     * @param <V>  值类型
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> get(String name, UnaryOperator<CacheBuilder<Object, Object>> spec) {
        return (Cache<K, V>) caches.computeIfAbsent(name,
                n -> spec.apply(CacheBuilder.newBuilder()).build());
    }

    /**
     * 查询指定缓存的统计信息（spec 未开启 recordStats 时为零值统计）
     *
     * @return 缓存名不存在时返回 null
     */
    public CacheStats stats(String name) {
        Cache<?, ?> cache = caches.get(name);
        return cache == null ? null : cache.stats();
    }

    public Map<String, CacheStats> statsAll() {
        return caches.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stats()));
    }

    /**
     * 清空指定缓存的全部条目
     */
    public void invalidateAll(String name) {
        Cache<?, ?> cache = caches.get(name);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    public Set<String> names() {
        return caches.keySet();
    }
}
