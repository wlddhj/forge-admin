package com.forge.admin.common.aspect;

import com.forge.admin.common.annotation.DataPermission;
import com.forge.admin.common.permission.DataPermissionContext;
import com.forge.admin.common.permission.DataPermissionContextHolder;
import com.forge.admin.common.permission.DataPermissionRule;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据权限注解拦截器
 *
 * 拦截 @DataPermission 注解，管理上下文栈
 * 基于 shi9-boot 设计
 *
 * @author standadmin
 * @since 2026-03-04
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class DataPermissionAnnotationInterceptor {

    @Around("@annotation(dataPermission)")
    public Object around(ProceedingJoinPoint point, DataPermission dataPermission) throws Throwable {
        // 检查是否启用数据权限
        if (!dataPermission.enable()) {
            log.debug("[数据权限拦截器] 数据权限已禁用，跳过");
            return point.proceed();
        }

        // 推入新的上下文
        DataPermissionContext context = DataPermissionContextHolder.push();
        context.setEnabled(true);

        try {
            // 添加 include 规则
            for (var ruleClass : dataPermission.rules()) {
                context.addIncludeRule(ruleClass);
                log.debug("[数据权限拦截器] 添加规则: {}", ruleClass.getSimpleName());
            }

            // 添加 exclude 规则
            for (var ruleClass : dataPermission.excludeRules()) {
                context.addExcludeRule(ruleClass);
                log.debug("[数据权限拦截器] 排除规则: {}", ruleClass.getSimpleName());
            }

            // 执行目标方法
            return point.proceed();

        } finally {
            // 弹出上下文
            DataPermissionContextHolder.pop();
            log.debug("[数据权限拦截器] 上下文已弹出，当前栈大小: {}", DataPermissionContextHolder.size());
        }
    }
}
