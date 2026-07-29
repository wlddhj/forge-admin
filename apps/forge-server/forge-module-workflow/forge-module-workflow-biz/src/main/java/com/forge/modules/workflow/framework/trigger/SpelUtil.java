package com.forge.modules.workflow.framework.trigger;

import com.aizuda.bpm.engine.core.Execution;

import java.util.Map;

/**
 * 流程触发器 SpEL 工具类
 *
 * <p>用于在触发器节点的 SpEL 表达式中读写流程变量或调用静态方法。
 * 通过 {@code T(...)} 类型引用语法在表达式里访问。</p>
 *
 * <p>典型用法（合同金额分级示例）:
 * <pre>{@code
 * T(com.forge.modules.workflow.framework.trigger.SpelUtil)
 *     .setVar(execution, 'riskLevel', amount >= 1000000 ? 'A' : (amount >= 100000 ? 'B' : 'C'))
 * }</pre>
 * </p>
 *
 * <p>注意：本类仅作为 SpEL 表达式可访问的"桥",不要在普通 Java 代码中直接调用。
 * 直接调用执行写入应通过 Service 层并遵循事务边界。</p>
 *
 * @author forge-admin
 */
public final class SpelUtil {

    private SpelUtil() {
    }

    /**
     * 设置流程变量到当前 Execution 上下文。
     *
     * <p>等价于 {@code execution.getArgs().put(key, value)}，
     * 但 null 安全；下游条件分支、条件审批、审批人表达式均可读取该变量。</p>
     *
     * @param execution FlowLong 执行上下文
     * @param key       变量名
     * @param value     变量值（任意类型）
     */
    public static void setVar(Execution execution, String key, Object value) {
        if (execution == null || key == null) {
            return;
        }
        Map<String, Object> args = execution.getArgs();
        if (args != null) {
            args.put(key, value);
        }
    }

    /**
     * 读取流程变量
     *
     * @param execution FlowLong 执行上下文
     * @param key       变量名
     * @return 变量值；不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T getVar(Execution execution, String key) {
        if (execution == null || key == null) {
            return null;
        }
        Map<String, Object> args = execution.getArgs();
        if (args == null) {
            return null;
        }
        return (T) args.get(key);
    }
}
