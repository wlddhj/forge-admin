package com.forge.modules.workflow.framework.trigger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 触发器 Bean 标记注解
 *
 * <p>标记一个 Spring Bean 为「可被流程触发器节点调用」的触发器。
 * 启动时 {@link TriggerBeanRegistry} 会扫描所有带此注解的 Bean,
 * 注册到可调用列表,供设计器下拉选择。</p>
 *
 * <p>安全性:只有带此注解的 Bean 才会被 {@code bean} 模式或 {@code delegateExpression}
 * 模式允许调用,起到白名单作用。{@code class} 模式不走 Spring,不受此限制。</p>
 *
 * <p>典型用法:
 * <pre>{@code
 * @Component
 * @FlowLongTrigger(name = "合同风险评估", description = "根据金额自动评估合同风险等级")
 * public class ContractRiskTrigger {
 *     public boolean execute(Execution execution, NodeModel nodeModel, Map<String, Object> ctx) {
 *         Long amount = (Long) ctx.get("amount");
 *         // ...
 *         return true;
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowLongTrigger {

    /**
     * 触发器展示名（前端下拉显示）
     */
    String name() default "";

    /**
     * 触发器描述（前端下拉显示副标题）
     */
    String description() default "";

    /**
     * 触发器分类标签（前端可按分类筛选）
     */
    String category() default "default";
}
