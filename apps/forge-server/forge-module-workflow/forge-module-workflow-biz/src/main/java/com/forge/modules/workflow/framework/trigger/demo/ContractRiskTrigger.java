package com.forge.modules.workflow.framework.trigger.demo;

import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.modules.workflow.framework.trigger.AbstractFlowLongTrigger;
import com.forge.modules.workflow.framework.trigger.FlowLongTrigger;
import com.forge.modules.workflow.framework.trigger.TriggerParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 合同风险评估触发器（示例）
 *
 * <p>演示 {@link AbstractFlowLongTrigger} 的两种典型用法:</p>
 * <ul>
 *   <li>{@link #execute(Execution, NodeModel, Map)} — 继承基类,使用工具方法(标准签名)</li>
 *   <li>{@link #evaluateFlexible(Execution, Long, String)} — 不继承基类,用
 *       {@link TriggerParam} 灵活形参注入(高级签名)</li>
 * </ul>
 *
 * <p>设计器下拉显示名: "合同风险评估（示例）"</p>
 */
@Component
@FlowLongTrigger(
        name = "合同风险评估（示例）",
        description = "根据合同金额自动计算风险等级(A/B/C),写入流程变量 riskLevel",
        category = "示例"
)
public class ContractRiskTrigger extends AbstractFlowLongTrigger {

    /**
     * 标准签名 — 继承基类,使用工具方法
     *
     * <p>TriggerInvoker 优先匹配此方法(基类约定),无需反射。</p>
     */
    @Override
    public boolean execute(Execution execution, NodeModel nodeModel, Map<String, Object> ctx) {
        // 必填参数 + 类型自动转换
        Long amount = requireArgAs(ctx, "amount", Long.class);

        String riskLevel = computeRiskLevel(amount);
        log.info("合同风险评估: amount={}, riskLevel={}", amount, riskLevel);

        // 写回流程变量(下游条件分支可读)
        setVar(execution, "riskLevel", riskLevel);
        return true;
    }

    /**
     * 灵活签名示例 — 不继承基类,用 {@link TriggerParam} 注入
     *
     * <p>TriggerInvoker 走反射路径,按注解解析形参。</p>
     */
    public String evaluateFlexible(Execution execution,
                                   @TriggerParam("amount") Long amount,
                                   @TriggerParam("industry") String industry) {
        if (amount == null) {
            return "UNKNOWN";
        }
        String level = computeRiskLevel(amount);
        log.info("合同风险评估(灵活签名): amount={}, industry={}, level={}", amount, industry, level);
        return level;
    }

    private String computeRiskLevel(long amount) {
        if (amount >= 1_000_000) return "A";
        if (amount >= 100_000) return "B";
        return "C";
    }
}
