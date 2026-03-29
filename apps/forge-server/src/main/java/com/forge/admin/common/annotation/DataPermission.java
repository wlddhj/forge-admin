package com.forge.admin.common.annotation;

import com.forge.admin.common.permission.DataPermissionRule;

import java.lang.annotation.*;

/**
 * 数据权限过滤注解
 *
 * 用于标注需要进行数据权限过滤的方法或类
 * 借鉴 shi9-boot 设计，支持灵活的规则配置
 *
 * @author standadmin
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /**
     * 部门表别名
     */
    String deptAlias() default "d";

    /**
     * 用户表别名
     */
    String userAlias() default "u";

    /**
     * 权限字符
     */
    String permission() default "";

    /**
     * 是否开启数据权限
     * 用于临时禁用数据权限过滤，避免循环依赖
     */
    boolean enable() default true;

    /**
     * 包含的数据权限规则
     * 可以指定特定的规则类来实现自定义数据权限逻辑
     */
    Class<? extends DataPermissionRule>[] rules() default {};

    /**
     * 排除的数据权限规则
     * 在包含的规则中排除指定的规则
     */
    Class<? extends DataPermissionRule>[] excludeRules() default {};
}
