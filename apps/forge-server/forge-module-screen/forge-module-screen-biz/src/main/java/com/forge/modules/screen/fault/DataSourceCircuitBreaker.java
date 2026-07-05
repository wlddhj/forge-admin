package com.forge.modules.screen.fault;

import com.forge.modules.screen.constant.ScreenConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 数据源熔断器。
 *
 * <p>
 * 1 分钟窗口内失败累计 10 次 → 熔断 30 秒；熔断期间 {@link #isTripped(Long)} 返回 true。
 * 成功调用会清空失败计数。
 * </p>
 *
 * <ul>
 * <li>失败计数 key：{@code screen:cb:count:{id}}，TTL 1 分钟（首次失败时设置）</li>
 * <li>熔断标志 key：{@code screen:cb:tripped:{id}}，TTL 30 秒</li>
 * </ul>
 *
 * @author standadmin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceCircuitBreaker {

    private static final int FAIL_THRESHOLD = 10;
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(1);
    private static final Duration TRIP_DURATION = Duration.ofSeconds(30);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 是否处于熔断状态。
     */
    public boolean isTripped(Long dataSourceId) {
        Object flag = redisTemplate.opsForValue().get(tripKey(dataSourceId));
        return flag != null;
    }

    /**
     * 记录一次失败，必要时触发熔断。
     */
    public void recordFailure(Long dataSourceId) {
        Long count = redisTemplate.opsForValue().increment(countKey(dataSourceId));
        if (count != null && count == 1L) {
            redisTemplate.expire(countKey(dataSourceId), FAIL_WINDOW);
        }
        if (count != null && count >= FAIL_THRESHOLD) {
            log.warn("数据源 {} 触发熔断（失败 {} 次）", dataSourceId, count);
            redisTemplate.opsForValue().set(tripKey(dataSourceId), "1", TRIP_DURATION);
            redisTemplate.delete(countKey(dataSourceId));
        }
    }

    /**
     * 记录一次成功，清空失败计数。
     */
    public void recordSuccess(Long dataSourceId) {
        redisTemplate.delete(countKey(dataSourceId));
    }

    private String countKey(Long id) {
        return ScreenConstants.CIRCUIT_BREAKER_PREFIX + "count:" + id;
    }

    private String tripKey(Long id) {
        return ScreenConstants.CIRCUIT_BREAKER_PREFIX + "tripped:" + id;
    }
}
