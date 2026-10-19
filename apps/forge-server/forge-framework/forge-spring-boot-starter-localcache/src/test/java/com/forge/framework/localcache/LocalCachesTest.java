package com.forge.framework.localcache;

import com.google.common.cache.Cache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCachesTest {

    private final LocalCaches localCaches = new LocalCaches();

    @Test
    void sameNameReturnsSameInstance() {
        Cache<String, String> c1 = localCaches.get("demo", b -> b.maximumSize(10));
        Cache<String, String> c2 = localCaches.get("demo", b -> b.maximumSize(999));
        assertThat(c1).isSameAs(c2);
        assertThat(localCaches.names()).containsExactly("demo");
    }

    @Test
    void differentNamesAreIndependent() {
        Cache<String, String> c1 = localCaches.get("a", b -> b.maximumSize(10));
        Cache<String, String> c2 = localCaches.get("b", b -> b.maximumSize(10));
        c1.put("k", "v1");
        c2.put("k", "v2");
        assertThat(c1.getIfPresent("k")).isEqualTo("v1");
        assertThat(c2.getIfPresent("k")).isEqualTo("v2");
    }

    @Test
    void maximumSizeEvicts() {
        Cache<Long, String> cache = localCaches.get("evict-test", b -> b.maximumSize(2));
        cache.put(1L, "a");
        cache.put(2L, "b");
        cache.put(3L, "c");
        cache.cleanUp();
        assertThat(cache.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void expireAfterWriteEvicts() throws InterruptedException {
        Cache<String, String> cache = localCaches.get("ttl-test",
                b -> b.expireAfterWrite(Duration.ofMillis(100)));
        cache.put("k", "v");
        assertThat(cache.getIfPresent("k")).isEqualTo("v");
        TimeUnit.MILLISECONDS.sleep(150);
        assertThat(cache.getIfPresent("k")).isNull();
    }

    @Test
    void loaderIsAtomicPerKey() throws Exception {
        Cache<String, Integer> cache = localCaches.get("single-flight", b -> b.maximumSize(10));
        int[] loaded = {0};
        Integer v = cache.get("k", () -> {
            loaded[0]++;
            return 42;
        });
        assertThat(v).isEqualTo(42);
        Integer again = cache.get("k", () -> {
            loaded[0]++;
            return -1;
        });
        assertThat(again).isEqualTo(42);
        assertThat(loaded[0]).isEqualTo(1);
    }

    @Test
    void recordStatsAndInvalidateAllWork() {
        Cache<String, String> cache = localCaches.get("stats-test",
                b -> b.maximumSize(10).recordStats());
        cache.put("k", "v");
        cache.getIfPresent("k");
        cache.getIfPresent("missing");
        assertThat(localCaches.stats("stats-test").hitCount()).isEqualTo(1);
        assertThat(localCaches.stats("stats-test").missCount()).isEqualTo(1);
        assertThat(localCaches.statsAll()).containsKey("stats-test");

        localCaches.invalidateAll("stats-test");
        assertThat(cache.getIfPresent("k")).isNull();

        assertThat(localCaches.stats("not-exists")).isNull();
    }
}
