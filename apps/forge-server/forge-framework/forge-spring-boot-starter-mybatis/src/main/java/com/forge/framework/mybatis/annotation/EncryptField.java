package com.forge.framework.mybatis.annotation;

import java.lang.annotation.*;

/**
 * 敏感字段加密注解
 * 标注在实体类字段上，MyBatis TypeHandler 自动加解密
 *
 * @author standadmin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EncryptField {

    /**
     * 是否加密（默认 true）
     * 可设置为 false 以临时禁用加密
     */
    boolean enabled() default true;
}