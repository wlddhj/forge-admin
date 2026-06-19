package com.forge.framework.web.annotation;

import java.lang.annotation.*;

/**
 * XSS 过滤忽略注解
 * 标注在 Controller 方法或类上，跳过 XSS 过滤
 *
 * @author standadmin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface XssIgnore {
}