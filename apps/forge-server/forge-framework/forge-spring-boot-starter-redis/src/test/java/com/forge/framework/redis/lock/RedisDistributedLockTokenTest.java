package com.forge.framework.redis.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 锁令牌化单元测试（稳定性批次 T5）：
 * unlock 仅以原子脚本删除本人令牌，杜绝"旧持有者误删他人新锁"。
 */
@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTokenTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisDistributedLock lock;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lock = new RedisDistributedLock(redisTemplate);
    }

    @AfterEach
    void cleanStack() {
        // 清理 ThreadLocal 残留（非配对用例后）
        tryUnlockQuietly();
    }

    private void tryUnlockQuietly() {
        try {
            lock.unlock("cleanup-key");
        } catch (Exception ignored) {
            // 仅清栈
        }
    }

    @Test
    @DisplayName("配对 try/unlock：以 tryLock 写入的令牌走原子脚本删除")
    void pairedUnlockShouldDeleteWithOwnToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        boolean locked = lock.tryLock("job", Duration.ofSeconds(30));
        org.junit.jupiter.api.Assertions.assertTrue(locked);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq("forge:lock:job"), tokenCaptor.capture(), any(Duration.class));

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        lock.unlock("job");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("forge:lock:job")), eq(new Object[]{tokenCaptor.getValue()}));
    }

    @Test
    @DisplayName("令牌不匹配（锁已被他人持有）：脚本返回 0，不误删且不抛错")
    void mismatchedTokenShouldNotDelete() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lock.tryLock("job", Duration.ofSeconds(30));

        // 模拟锁已过期、他人持新锁：GET != 本人令牌 → 脚本返回 0
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertDoesNotThrow(() -> lock.unlock("job"));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("非配对 unlock（本线程未持锁）：不执行脚本")
    void unpairedUnlockShouldSkip() {
        lock.unlock("never-locked");
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("同线程嵌套两把锁：LIFO 配对释放")
    void nestedLocksShouldReleaseInLifoOrder() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lock.tryLock("a", Duration.ofSeconds(30));
        lock.tryLock("b", Duration.ofSeconds(30));

        lenient().when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        lock.unlock("b");
        lock.unlock("a");
        // 两次释放均执行脚本（无 warn 跳过）即 LIFO 配对成功
        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
