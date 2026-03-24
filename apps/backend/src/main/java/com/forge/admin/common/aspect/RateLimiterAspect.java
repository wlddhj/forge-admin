package com.forge.admin.common.aspect;

import com.forge.admin.common.annotation.RateLimiter;
import com.forge.admin.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

/**
 * 限流切面
 *
 * @author standadmin
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    /**
     * 使用 StringRedisTemplate 避免 JSON 序列化问题
     * Lua 脚本需要纯字符串参数
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lua 脚本：实现令牌桶算法
     * 返回值：1 表示允许访问，0 表示限流
     * 使用不带 SHA 的版本，避免缓存问题
     */
    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1]) or 0\n" +    // 安全的 tonumber，默认 0
            "local expire = tonumber(ARGV[2]) or 60\n" +  // 安全的 tonumber，默认 60
            "local current = redis.call('get', key)\n" +
            "if current == false then\n" +
            "    current = 0\n" +
            "else\n" +
            "    current = tonumber(current)\n" +
            "    if not current or current == nil then\n" +  // 双重检查
            "        current = 0\n" +
            "    end\n" +
            "end\n" +
            "if current + 1 > limit then\n" +
            "    return 0\n" +
            "else\n" +
            "    redis.call('incrby', key, 1)\n" +
            "    if current == 0 then\n" +
            "        redis.call('expire', key, expire)\n" +
            "    end\n" +
            "    return 1\n" +
            "end";

    /**
     * 拦截带有 @RateLimiter 注解的方法
     */
    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint joinPoint, RateLimiter rateLimiter) {
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = getClientIP(request);

        // 构建限流 key
        String key = rateLimiter.key().replace("#root.ip", ip);

        // 执行 Lua 脚本（不使用 SHA 缓存）
        RedisScript<Long> redisScript = RedisScript.of(LUA_SCRIPT, Long.class);
        // 使用 StringRedisTemplate 避免 JSON 序列化问题
        Long result = stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(rateLimiter.count()),
                String.valueOf(rateLimiter.time())
        );

        if (result == null || result == 0) {
            log.warn("限流触发: IP={}, 方法={}", ip, joinPoint.getSignature().getName());
            throw new BusinessException(429, rateLimiter.message());
        }

        log.debug("限流检查通过: IP={}, Key={}", ip, key);
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个 IP 的情况（X-Forwarded-For 可能包含多个 IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
