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
     * 限流 key 类型
     */
    KeyType keyType() default KeyType.IP;

    /**
     * 限流 key 前缀
     */
    String keyPrefix() default "rate_limit";

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

    /**
     * 限流 Key 类型枚举
     */
    enum KeyType {
        /**
         * 按 IP 限流
         */
        IP,
        /**
         * 按用户名限流（从方法参数中提取 username 字段）
         */
        USERNAME
    }
}
