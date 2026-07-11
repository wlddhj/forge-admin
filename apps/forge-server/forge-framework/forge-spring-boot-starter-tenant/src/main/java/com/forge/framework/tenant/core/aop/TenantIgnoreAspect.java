package com.forge.framework.tenant.core.aop;

import com.forge.framework.tenant.core.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Slf4j
@Aspect
public class TenantIgnoreAspect {

    @Around("@annotation(tenantIgnore) || @within(tenantIgnore)")
    public Object around(ProceedingJoinPoint joinPoint, TenantIgnore tenantIgnore) throws Throwable {
        Boolean previous = TenantContextHolder.isIgnore() ? Boolean.TRUE : null;
        TenantContextHolder.setIgnore(true);
        try {
            return joinPoint.proceed();
        } finally {
            if (previous == null) {
                TenantContextHolder.setIgnore(null);
            } else {
                TenantContextHolder.setIgnore(previous);
            }
        }
    }
}