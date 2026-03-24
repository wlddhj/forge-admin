package com.forge.admin.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * @author standadmin
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /**
     * 限流 key（支持 SpEL 表达式）
     * 默认使用 IP 地址
     */
    String key() default "'rate_limit:' + #root.ip";

    /**
     * 时间窗口（秒）
     */
    long time() default 60;

    /**
     * 时间窗口内最大请求次数
     */
    long count() default 100;

    /**
     * 限流提示信息
     */
    String message() default "访问过于频繁，请稍后再试";
}
