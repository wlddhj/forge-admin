package com.forge.modules.workflow.framework.trigger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 触发器方法形参注入注解
 *
 * <p>标注在 {@code @FlowLongTrigger} Bean 的方法形参上,
 * 触发器执行时按 {@link #value()} 指定的 key 从流程变量 ctx 中取值注入。
 * 形参类型必须是基本类型或其包装类、String、或其他可从 String 转换的目标类型。</p>
 *
 * <p>示例:
 * <pre>{@code
 * public boolean evaluate(
 *     Execution execution,
 *     @TriggerParam("amount") Long amount,
 *     @TriggerParam("industry") String industry
 * ) {
 *     // amount 和 industry 由触发器框架自动从 ctx 注入
 *     return amount != null && amount > 100000;
 * }
 * }</pre>
 * </p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TriggerParam {

    /**
     * 流程变量名（key）
     */
    String value();

    /**
     * 当 ctx 中找不到该 key 时,是否允许 null
     */
    boolean required() default false;
}
