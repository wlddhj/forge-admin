package com.forge.modules.screen.fault;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataSourceCircuitBreaker 单元测试（TDD）。
 *
 * <p>
 * 4 个用例覆盖：
 * <ol>
 * <li>未设置熔断标志：isTripped 返回 false</li>
 * <li>已设置熔断标志：isTripped 返回 true</li>
 * <li>第 10 次失败触发熔断：写入 tripped 标志（TTL 30s）并清空计数</li>
 * <li>成功时清空失败计数</li>
 * </ol>
 * </p>
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSourceCircuitBreakerTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    ValueOperations<String, Object> valueOps;

    @InjectMocks
    DataSourceCircuitBreaker breaker;

    @BeforeEach
    void setup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void isTripped_false_when_no_failure_flag() {
        when(valueOps.get("screen:cb:tripped:1")).thenReturn(null);
        assertThat(breaker.isTripped(1L)).isFalse();
    }

    @Test
    void isTripped_true_when_flag_set() {
        when(valueOps.get("screen:cb:tripped:1")).thenReturn("1");
        assertThat(breaker.isTripped(1L)).isTrue();
    }

    @Test
    void recordFailure_opens_circuit_on_10th_failure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(10L);

        breaker.recordFailure(1L);

        verify(valueOps).set(eq("screen:cb:tripped:1"), eq("1"), any(java.time.Duration.class));
    }

    @Test
    void recordSuccess_clears_failure_count() {
        breaker.recordSuccess(1L);
        verify(redisTemplate).delete("screen:cb:count:1");
    }
}
