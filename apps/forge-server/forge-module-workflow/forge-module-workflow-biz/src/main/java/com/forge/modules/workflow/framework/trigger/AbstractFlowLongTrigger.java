package com.forge.modules.workflow.framework.trigger;

import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.model.NodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 流程触发器 Bean 抽象基类
 *
 * <p>约定:所有通过 {@code bean} 模式或 {@code delegateExpression} 模式被流程触发器节点
 * 调用的 Spring Bean,推荐继承本基类并实现 {@link #execute(Execution, NodeModel, Map)}。</p>
 *
 * <p>基类提供常用工具方法,减少样板代码:
 * <ul>
 *   <li>{@link #getArg(Map, String)} / {@link #getArg(Map, String, Object)} — 读 ctx 变量,带默认值</li>
 *   <li>{@link #getArgAs(Map, String, Class)} — 读 ctx 变量并自动类型转换</li>
 *   <li>{@link #setVar(Execution, String, Object)} — 写流程变量(下游节点可读)</li>
 *   <li>{@link #requireArg(Map, String)} / {@link #requireArgAs(Map, String, Class)} — 必填参数检查</li>
 *   <li>{@link #log} — 已初始化好的 Logger,子类直接 {@code log.info(...)}</li>
 * </ul>
 * </p>
 *
 * <p>典型用法:
 * <pre>{@code
 * @Component
 * @FlowLongTrigger(name = "合同风险评估", description = "根据金额计算风险等级")
 * public class ContractRiskTrigger extends AbstractFlowLongTrigger {
 *     @Override
 *     public boolean execute(Execution execution, NodeModel nodeModel, Map<String, Object> ctx) {
 *         Long amount = requireArgAs(ctx, "amount", Long.class);
 *         String riskLevel = amount >= 1_000_000 ? "A" : amount >= 100_000 ? "B" : "C";
 *         setVar(execution, "riskLevel", riskLevel);
 *         log.info("合同 {} 评估为 {} 级", amount, riskLevel);
 *         return true;
 *     }
 * }
 * }</pre>
 * </p>
 *
 * <p>注意:本基类不强制使用 — 已有 {@code @TriggerParam} 灵活签名机制的 Bean 可不继承
 * 本基类,TriggerInvoker 会自动 fallback 到反射 + 注解注入路径。但新 Bean 强烈建议继承
 * 以获得 IDE 提示、类型安全与可读性。</p>
 */
public abstract class AbstractFlowLongTrigger {

    /** 子类可直接使用的 logger,自动绑定实际子类 */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 触发器执行入口。子类必须实现。
     *
     * @param execution FlowLong 执行上下文
     * @param nodeModel 当前节点模型
     * @param ctx       流程变量(来自 {@code execution.getArgs()},可能为 null)
     * @return true 触发成功继续流转;false 触发失败,流程暂停(若配置了 continueOnError 则忽略)
     */
    public abstract boolean execute(Execution execution, NodeModel nodeModel, Map<String, Object> ctx);

    // ===== 工具方法:读 ctx =====

    /**
     * 读取 ctx 中的变量,不存在返回 null
     */
    protected Object getArg(Map<String, Object> ctx, String key) {
        return ctx == null ? null : ctx.get(key);
    }

    /**
     * 读取 ctx 中的变量,不存在返回默认值
     */
    protected <T> T getArg(Map<String, Object> ctx, String key, T defaultValue) {
        if (ctx == null) return defaultValue;
        T value = (T) ctx.get(key);
        return value == null ? defaultValue : value;
    }

    /**
     * 读取 ctx 中的变量并转为目标类型(支持 String/Number/Boolean 互转)
     *
     * @param ctx        流程变量
     * @param key        变量名
     * @param targetType 目标类型
     * @return 转换后的值;不存在或转换失败返回 null
     */
    @SuppressWarnings("unchecked")
    protected <T> T getArgAs(Map<String, Object> ctx, String key, Class<T> targetType) {
        Object value = getArg(ctx, key);
        return convert(value, targetType);
    }

    // ===== 工具方法:写 ctx =====

    /**
     * 设置流程变量,下游节点(包括条件分支、条件审批、审批人表达式)可读
     */
    protected void setVar(Execution execution, String key, Object value) {
        SpelUtil.setVar(execution, key, value);
    }

    // ===== 工具方法:必填校验 =====

    /**
     * 必填参数读取,缺失抛 IllegalArgumentException(触发器节点会转为触发失败)
     */
    protected Object requireArg(Map<String, Object> ctx, String key) {
        Object value = getArg(ctx, key);
        if (value == null) {
            throw new IllegalArgumentException("触发器 " + getClass().getSimpleName() + " 缺少必填参数: " + key);
        }
        return value;
    }

    /**
     * 必填参数读取并类型转换
     */
    protected <T> T requireArgAs(Map<String, Object> ctx, String key, Class<T> targetType) {
        T value = getArgAs(ctx, key, targetType);
        if (value == null) {
            throw new IllegalArgumentException(
                    "触发器 " + getClass().getSimpleName() + " 缺少必填参数或类型错误: " + key
                            + " (期望 " + targetType.getSimpleName() + ")");
        }
        return value;
    }

    // ===== 内部 =====

    @SuppressWarnings("unchecked")
    private <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        try {
            if (targetType == Long.class || targetType == long.class) {
                return (T) Long.valueOf(value.toString());
            }
            if (targetType == Integer.class || targetType == int.class) {
                return (T) Integer.valueOf(value.toString());
            }
            if (targetType == Double.class || targetType == double.class) {
                return (T) Double.valueOf(value.toString());
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return (T) Boolean.valueOf(value.toString());
            }
            if (targetType == String.class) {
                return (T) value.toString();
            }
        } catch (Exception e) {
            log.warn("类型转换失败: value={} → {}, 保持 null", value, targetType.getName());
        }
        return null;
    }
}
