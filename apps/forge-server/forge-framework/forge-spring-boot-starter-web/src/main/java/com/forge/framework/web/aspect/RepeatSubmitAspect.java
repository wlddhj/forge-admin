package com.forge.framework.web.aspect;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.IpUtils;
import com.forge.common.utils.UserContext;
import com.forge.framework.web.annotation.RepeatSubmit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;

/**
 * 防重复提交切面
 *
 * Redis SET NX EX 原子占锁：间隔期内相同用户 + URL + 参数的重复请求直接拒绝；
 * 业务执行异常时删除锁，允许用户修正后立即重试
 *
 * @author standadmin
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RepeatSubmitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * SET NX EX 原子占锁：返回 1=占锁成功，0=已存在（重复提交）
     */
    private static final String LUA_SCRIPT =
            "if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', tonumber(ARGV[2])) then\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        String key = buildKey(request, joinPoint);

        // SET EX 最小粒度 1 秒，亚秒配置向上取整
        long seconds = Math.max(1, repeatSubmit.timeUnit().toSeconds(repeatSubmit.interval()));

        Long result = stringRedisTemplate.execute(
                RedisScript.of(LUA_SCRIPT, Long.class),
                Collections.singletonList(key),
                "1", String.valueOf(seconds));

        if (result == null || result == 0) {
            log.warn("重复提交拦截: key={}, 方法={}", key, joinPoint.getSignature().toShortString());
            throw new BusinessException(ResultCode.REPEAT_SUBMIT.getCode(), repeatSubmit.message());
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务失败释放锁允许立即重试；删除失败则等待 TTL 自然过期
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception ex) {
                log.warn("释放防重复提交锁失败，等待 TTL 过期: key={}", key, ex);
            }
            throw e;
        }
    }

    /**
     * key = repeat_submit:{u:userId | ip:客户端IP}:{requestURI}:{md5(参数摘要)}
     * app 端接口不填充 UserContext，userId 为空时回退 IP 维度
     */
    private String buildKey(HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        Long userId = UserContext.getCurrentUserId();
        String identity = userId != null ? "u:" + userId : "ip:" + IpUtils.getClientIp(request);
        return "repeat_submit:" + identity + ":" + request.getRequestURI() + ":" + argsDigest(joinPoint);
    }

    /**
     * 参数摘要：跳过 Servlet/校验对象；文件取名称+大小；其余 JSON 序列化后整体 MD5
     */
    private String argsDigest(ProceedingJoinPoint joinPoint) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse || arg instanceof BindingResult) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                sb.append(file.getName()).append(':').append(file.getSize()).append(';');
                continue;
            }
            try {
                sb.append(JSONUtil.toJsonStr(arg)).append(';');
            } catch (Exception e) {
                sb.append(String.valueOf(arg)).append(';');
            }
        }
        return DigestUtil.md5Hex(sb.toString());
    }
}
