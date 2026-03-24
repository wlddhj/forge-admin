package com.forge.admin.common.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * 用于标注需要进行数据权限过滤的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 部门表的别名
     */
    String deptAlias() default "d";

    /**
     * 用户表的别名
     */
    String userAlias() default "u";

    /**
     * 部门ID字段名
     */
    String deptIdField() default "dept_id";
}
