package com.forge.framework.tenant.core.job;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Slf4j
@Aspect
public class TenantJobAspect {

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled) || @within(com.forge.framework.tenant.core.job.TenantJob)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("[tenant-job] 定时任务 {} 未指定 tenantId，将忽略租户过滤",
                    joinPoint.getSignature().toShortString());
            TenantContextHolder.setIgnore(true);
        }
        try {
            return joinPoint.proceed();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
