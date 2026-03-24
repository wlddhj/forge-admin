package com.forge.admin.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 *
 * @author standadmin
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作标题
     */
    String title() default "";

    /**
     * 业务类型
     */
    BusinessType businessType() default BusinessType.OTHER;

    /**
     * 是否保存请求参数
     */
    boolean isSaveRequestData() default true;

    /**
     * 是否保存响应结果
     */
    boolean isSaveResponseData() default true;

    /**
     * 业务类型枚举
     */
    enum BusinessType {
        /**
         * 其它
         */
        OTHER,
        /**
         * 新增
         */
        INSERT,
        /**
         * 修改
         */
        UPDATE,
        /**
         * 删除
         */
        DELETE,
        /**
         * 授权
         */
        GRANT,
        /**
         * 导出
         */
        EXPORT,
        /**
         * 导入
         */
        IMPORT,
        /**
         * 强退
         */
        FORCE,
        /**
         * 清空数据
         */
        CLEAN
    }
}
