package com.forge.framework.tenant.core.job;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注定时任务在指定租户上下文执行
 *
 * 用法：在 Quartz Job 类或方法上标注，配合 TenantJobAspect 自动注入 tenantId
 *
 * 注意：当前实现中 tenantId 仍需通过 JobDataMap 传入；本注解主要起标记作用
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantJob {
}
