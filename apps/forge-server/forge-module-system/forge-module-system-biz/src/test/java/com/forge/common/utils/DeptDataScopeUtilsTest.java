package com.forge.common.utils;

import com.forge.common.utils.DeptDataScopeUtils.DeptInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DeptDataScopeUtilsTest {

    @AfterEach
    void cleanUp() {
        DeptDataScopeUtils.clearCache();
    }

    private DeptInfo dept(Long id, Long parentId) {
        return new DeptInfo(id, parentId, "0");
    }

    @Test
    void buildThenQueryReturnsFullSubtree() {
        DeptDataScopeUtils.buildDeptTreeCache(List.of(
                dept(1L, 0L), dept(2L, 1L), dept(3L, 2L), dept(4L, 1L)));

        assertThat(DeptDataScopeUtils.getChildDeptIds(1L)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(DeptDataScopeUtils.getChildDeptIds(2L)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(DeptDataScopeUtils.isChildDept(1L, 3L)).isTrue();
        assertThat(DeptDataScopeUtils.isChildDept(3L, 1L)).isFalse();
    }

    @Test
    void clearCacheDegradesToSelfOnly() {
        DeptDataScopeUtils.buildDeptTreeCache(List.of(dept(1L, 0L), dept(2L, 1L)));
        DeptDataScopeUtils.clearCache();
        assertThat(DeptDataScopeUtils.getChildDeptIds(1L)).containsExactly(1L);
    }

    @Test
    void rebuildIsImmediatelyVisibleAndConcurrentReadsNeverSeePartialTree() throws Exception {
        // 初始树：1 -> 2
        DeptDataScopeUtils.buildDeptTreeCache(List.of(dept(1L, 0L), dept(2L, 1L)));

        AtomicBoolean failed = new AtomicBoolean(false);
        int readers = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readers);
        ExecutorService pool = Executors.newFixedThreadPool(readers);
        for (int i = 0; i < readers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int n = 0; n < 200; n++) {
                        // 重建期间每次读到的只能是「旧完整树 1,2」或「新完整树 1,2,3」，
                        // 出现 singleton（部分树/未初始化）即原子性破坏
                        List<Long> ids = DeptDataScopeUtils.getChildDeptIds(1L);
                        if (ids.size() < 2) {
                            failed.set(true);
                            return;
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        for (int n = 0; n < 50; n++) {
            DeptDataScopeUtils.buildDeptTreeCache(List.of(
                    dept(1L, 0L), dept(2L, 1L), dept(3L, 1L)));
        }
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(failed.get()).as("并发读不应观察到部分树").isFalse();
        // 重建后立即可见完整新树（写后读一致性）
        assertThat(DeptDataScopeUtils.getChildDeptIds(1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }
}
