package com.forge.framework.plugin.guard;

import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 插件请求闸切面：拦截标注 {@link PluginGuard} 的 Controller。
 *
 * <p>语义（fail-closed）：
 * <ul>
 *   <li>插件管理模块未装配（无 Resolver Bean）：放行并 WARN 一次（底座可裁剪）</li>
 *   <li>请求无租户上下文（异步线程等）：拒绝</li>
 *   <li>租户未启用该插件 / 平台总闸禁用 / 未注册：拒绝</li>
 * </ul>
 *
 * @author forge-admin
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class PluginGuardAspect {

    private final ObjectProvider<PluginStateResolver> resolverProvider;

    /** 「未装配 Resolver」的 WARN 只打一次，避免每请求刷屏 */
    private final AtomicBoolean warnedNoResolver = new AtomicBoolean(false);

    @Around("@within(pluginGuard)")
    public Object around(ProceedingJoinPoint pjp, PluginGuard pluginGuard) throws Throwable {
        PluginStateResolver resolver = resolverProvider.getIfAvailable();
        if (resolver == null) {
            if (warnedNoResolver.compareAndSet(false, true)) {
                log.warn("未装配 PluginStateResolver（插件管理模块缺失），@PluginGuard({}) 放行", pluginGuard.value());
            }
            return pjp.proceed();
        }
        UserContext context = UserContext.get();
        Long tenantId = context == null ? null : context.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ResultCode.PLUGIN_DISABLED,
                    "插件[" + pluginGuard.value() + "]请求缺少租户上下文，已拒绝");
        }
        if (!resolver.isEnabled(tenantId, pluginGuard.value())) {
            throw new BusinessException(ResultCode.PLUGIN_DISABLED,
                    "插件[" + pluginGuard.value() + "]未启用或授权已失效，请联系管理员");
        }
        return pjp.proceed();
    }
}
