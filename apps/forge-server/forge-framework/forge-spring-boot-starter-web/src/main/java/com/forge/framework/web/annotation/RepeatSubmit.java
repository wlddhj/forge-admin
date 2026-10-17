package com.forge.framework.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交注解
 *
 * 同一用户（未登录时按 IP）在间隔期内对同一 URL 提交相同参数的请求将被拦截；
 * 业务执行失败会立即释放防重锁，允许用户修正后重试
 *
 * @author standadmin
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /**
     * 防重间隔
     */
    long interval() default 5;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 拦截提示信息
     */
    String message() default "请求正在处理中，请勿重复提交";
}
