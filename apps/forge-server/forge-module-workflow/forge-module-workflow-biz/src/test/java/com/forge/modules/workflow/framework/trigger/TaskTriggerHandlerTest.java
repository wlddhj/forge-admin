package com.forge.modules.workflow.framework.trigger;

import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.exception.FlowLongException;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.common.exception.BusinessException;
import com.forge.modules.workflow.framework.trigger.demo.ContractRiskTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 任务触发器处理器单测：SpEL 安全收敛（SimpleEvaluationContext）+ class 模式下线 + Bean 模式回归
 */
class TaskTriggerHandlerTest {

    private TriggerBeanRegistry registry;
    private TaskTriggerHandler handler;

    @BeforeEach
    void setUp() {
        registry = mock(TriggerBeanRegistry.class);
        handler = new TaskTriggerHandler(registry, new TriggerInvoker());
    }

    private NodeModel triggerNode(Map<String, Object> extendConfig) {
        NodeModel node = new NodeModel();
        node.setNodeKey("t1");
        node.setNodeName("触发器1");
        node.setExtendConfig(extendConfig);
        return node;
    }

    private Execution execution(Map<String, Object> args) {
        return new Execution(FlowCreator.of("1", "发起人"), new HashMap<>(args));
    }

    /**
     * finish 回调：记录是否被调用（流程是否继续流转）
     */
    private Function<Execution, Boolean> finishFlag(AtomicBoolean finished) {
        return e -> {
            finished.set(true);
            return true;
        };
    }

    @Test
    @DisplayName("普通表达式（变量比较，既有合法用法）→ 求值 true，继续流转")
    void plainExpressionEvaluatesTrue() {
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "expression");
        config.put("triggerExpression", "#amount > 1000000");
        AtomicBoolean finished = new AtomicBoolean(false);

        boolean result = handler.execute(triggerNode(config), execution(Map.of("amount", 2_000_000L)), finishFlag(finished));

        assertTrue(result, "表达式求值 true 应完成流转");
        assertTrue(finished.get(), "finish 回调应被调用");
    }

    @Test
    @DisplayName("普通表达式求值 false → 语义不变，按业务返回 false 抛 FlowLongException")
    void plainExpressionBusinessFalseKeepsSemantics() {
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "expression");
        config.put("triggerExpression", "#amount > 1000000");

        FlowLongException ex = assertThrows(FlowLongException.class,
                () -> handler.execute(triggerNode(config), execution(Map.of("amount", 1_000L)), finishFlag(new AtomicBoolean())));
        assertTrue(ex.getMessage().contains("业务返回 false"), "非安全类失败仍应翻译为业务返回 false: " + ex.getMessage());
    }

    @Test
    @DisplayName("表达式含 T(java.lang.Runtime) 静态引用 → 求值被拒，continueOnError=true 不卡主流程")
    void typeReferenceRejectedWithoutBlockingFlow() {
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "expression");
        config.put("triggerExpression", "T(java.lang.Runtime).getRuntime().exec('echo pwned') != null");
        config.put("continueOnError", true);
        AtomicBoolean finished = new AtomicBoolean(false);

        boolean result = handler.execute(triggerNode(config), execution(Map.of()), finishFlag(finished));

        assertTrue(result, "continueOnError=true 时触发器失败不应卡流程");
        assertTrue(finished.get(), "finish 回调应被调用");
    }

    @Test
    @DisplayName("表达式含 T(...) 静态引用 → 无 continueOnError 时抛明确求值失败错误")
    void typeReferenceRejectedWithClearError() {
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "expression");
        config.put("triggerExpression", "T(java.lang.Runtime).getRuntime().exec('echo pwned') != null");

        FlowLongException ex = assertThrows(FlowLongException.class,
                () -> handler.execute(triggerNode(config), execution(Map.of()), finishFlag(new AtomicBoolean())));
        assertTrue(ex.getMessage().contains("SpEL 表达式求值失败"), "应给出明确的求值失败错误: " + ex.getMessage());
    }

    @Test
    @DisplayName("表达式含 @bean 引用 → 求值被拒（无 Bean 解析器），不卡主流程（continueOnError）")
    void beanReferenceRejected() {
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "expression");
        config.put("triggerExpression", "@auditService.logEvent('x')");
        config.put("continueOnError", true);
        AtomicBoolean finished = new AtomicBoolean(false);

        boolean result = handler.execute(triggerNode(config), execution(Map.of()), finishFlag(finished));

        assertTrue(result, "continueOnError=true 时触发器失败不应卡流程");
        assertTrue(finished.get());
    }

    @Test
    @DisplayName("class 模式配置 → 抛“已不再支持”业务异常，不做反射实例化")
    void classModeUnsupported() {
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "class");
        config.put("triggerClass", "java.lang.Runtime");
        config.put("triggerMethod", "exec");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> handler.execute(triggerNode(config), execution(Map.of()), finishFlag(new AtomicBoolean())));
        assertEquals("class 模式触发器已不再支持，请使用 @FlowLongTrigger Bean 模式", ex.getMessage());
    }

    @Test
    @DisplayName("@FlowLongTrigger Bean 模式（ContractRiskTrigger）→ 照常工作并写回流程变量")
    void beanModeWithContractRiskTriggerStillWorks() {
        registerContractRiskTrigger();
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "bean");
        config.put("triggerBean", "contractRiskTrigger");
        config.put("triggerMethod", "execute");
        AtomicBoolean finished = new AtomicBoolean(false);

        Execution execution = execution(Map.of("amount", 150_000L));
        boolean result = handler.execute(triggerNode(config), execution, finishFlag(finished));

        assertTrue(result, "Bean 模式触发器应照常成功");
        assertTrue(finished.get());
        assertEquals("B", execution.getArgs().get("riskLevel"), "应写回流程变量 riskLevel");
    }

    @Test
    @DisplayName("delegateExpression 模式（字符串拼接/三元求 Bean 名）→ 照常工作")
    void delegateExpressionStillWorks() {
        registerContractRiskTrigger();
        Map<String, Object> config = new HashMap<>();
        config.put("triggerType", "delegateExpression");
        config.put("triggerExpression", "'contract' + 'RiskTrigger'");
        config.put("triggerMethod", "execute");
        AtomicBoolean finished = new AtomicBoolean(false);

        Execution execution = execution(Map.of("amount", 2_000_000L));
        boolean result = handler.execute(triggerNode(config), execution, finishFlag(finished));

        assertTrue(result, "delegateExpression 求值 Bean 名应照常成功");
        assertTrue(finished.get());
        assertEquals("A", execution.getArgs().get("riskLevel"));
    }

    private void registerContractRiskTrigger() {
        ContractRiskTrigger trigger = new ContractRiskTrigger();
        TriggerBeanRegistry.TriggerBeanInfo info = new TriggerBeanRegistry.TriggerBeanInfo();
        info.setBeanName("contractRiskTrigger");
        info.setBean(trigger);
        info.setTargetClass(ContractRiskTrigger.class);
        when(registry.get("contractRiskTrigger")).thenReturn(info);
    }
}
