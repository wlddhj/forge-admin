package com.forge.modules.screen.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataSourceCacheService 单元测试（TDD）。
 *
 * <p>
 * 4 个用例覆盖：
 * <ol>
 * <li>命中缓存：直接返回，不调 loader</li>
 * <li>缓存未命中：调 loader 并按 TTL 写回 Redis</li>
 * <li>单飞 + double-check：连续两次调用同一 key，loader 只执行一次</li>
 * <li>cacheKey 包含 dataSourceId 与 paramsJson：不同参数产生不同 key，相同参数稳定输出</li>
 * </ol>
 * </p>
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSourceCacheServiceTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    ValueOperations<String, Object> valueOps;

    @InjectMocks
    DataSourceCacheService cache;

    @BeforeEach
    void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void returns_cached_value_without_loading() {
        when(valueOps.get("screen:ds:abc")).thenReturn("cached");
        Object result = cache.getOrLoad("screen:ds:abc", 60, () -> "fresh");
        assertThat(result).isEqualTo("cached");
    }

    @Test
    void loads_and_caches_when_miss() {
        when(valueOps.get(anyString())).thenReturn(null);
        Supplier<String> loader = () -> "fresh";
        Object result = cache.getOrLoad("screen:ds:abc", 60, loader);
        assertThat(result).isEqualTo("fresh");
        verify(valueOps).set(eq("screen:ds:abc"), eq("fresh"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void singleflight_when_concurrent_loaders() {
        // 用 ConcurrentHashMap 模拟 Redis：set 后 get 能读到，验证 double-check 命中
        ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
        when(valueOps.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), any(), any(Duration.class));

        AtomicInteger counter = new AtomicInteger(0);
        Supplier<Integer> loader = () -> {
            counter.incrementAndGet();
            return 42;
        };
        cache.getOrLoad("screen:ds:abc", 60, loader);
        cache.getOrLoad("screen:ds:abc", 60, loader);
        // 第二次应命中刚刚写入的缓存（double-check 成功）
        verify(valueOps, times(1)).set(anyString(), any(), any(Duration.class));
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void cacheKey_includes_params_hash() {
        String k1 = cache.cacheKey(1L, "{\"status\":0}");
        String k2 = cache.cacheKey(1L, "{\"status\":1}");
        String k3 = cache.cacheKey(1L, "{\"status\":0}");
        assertThat(k1).isNotEqualTo(k2);
        assertThat(k1).isEqualTo(k3);
    }
}
