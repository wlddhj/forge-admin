package com.forge.modules.workflow.framework.trigger;

import com.aizuda.bpm.engine.TaskTrigger;
import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.exception.FlowLongException;
import com.aizuda.bpm.engine.model.NodeModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 任务触发器处理器
 *
 * <p>支持 4 种业务模式,通过 {@code extendConfig.triggerType} 路由:</p>
 * <ul>
 *   <li>{@code expression} — SpEL 求值,返回 boolean</li>
 *   <li>{@code bean} — Spring 容器拿 Bean,反射调方法</li>
 *   <li>{@code class} — 反射 .forName().newInstance(),调方法</li>
 *   <li>{@code delegateExpression} — SpEL 求出 Bean 名,再走 bean 路径</li>
 * </ul>
 *
 * <p>两个语义分层:</p>
 * <ul>
 *   <li>同步/异步（FlowLong 引擎层）— 由 {@code TaskServiceImpl.executeTaskTrigger}
 *       读取 {@code NodeModel.triggerType} (1=立即/2=延迟) 决定,本 handler 不再判断。</li>
 *   <li>业务模式（流程设计者配置）— 本 handler 关心,存于 {@code extendConfig.triggerType} (String)。</li>
 * </ul>
 */
@Slf4j
@Component
public class TaskTriggerHandler implements TaskTrigger {

    /** 业务模式常量 */
    public static final String MODE_EXPRESSION = "expression";
    public static final String MODE_BEAN = "bean";
    public static final String MODE_CLASS = "class";
    public static final String MODE_DELEGATE_EXPRESSION = "delegateExpression";

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ApplicationContext applicationContext;
    private final TriggerBeanRegistry triggerBeanRegistry;
    private final TriggerInvoker triggerInvoker;

    public TaskTriggerHandler(ApplicationContext applicationContext,
                              TriggerBeanRegistry triggerBeanRegistry,
                              TriggerInvoker triggerInvoker) {
        this.applicationContext = applicationContext;
        this.triggerBeanRegistry = triggerBeanRegistry;
        this.triggerInvoker = triggerInvoker;
    }

    @Override
    public boolean execute(NodeModel nodeModel, Execution execution, Function<Execution, Boolean> finish) {
        String nodeKey = nodeModel.getNodeKey();
        String nodeName = nodeModel.getNodeName();
        log.info("执行触发器: nodeKey={}, nodeName={}", nodeKey, nodeName);

        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        if (extendConfig == null) {
            log.warn("触发器节点 {} 未配置 extendConfig, 直接完成", nodeKey);
            return finish.apply(execution);
        }

        String mode = asString(extendConfig.get("triggerType"));
        if (mode == null || mode.isEmpty()) {
            mode = MODE_EXPRESSION; // 兼容历史数据
        }

        try {
            boolean success = dispatch(mode, extendConfig, nodeModel, execution);
            if (success) {
                log.info("触发器执行成功: nodeKey={}, mode={}", nodeKey, mode);
                return finish.apply(execution);
            } else {
                // 业务返回 false 视为失败,翻译为异常,统一受 continueOnError 控制。
                // 直接 return false 会被 FlowLong 引擎的 Assert.isFalse 抛裸 IllegalArgumentException,
                // 不受 continueOnError 影响。
                throw new FlowLongException(
                        "触发器节点「" + nodeName + "」业务返回 false (校验未通过)");
            }
        } catch (Exception e) {
            log.error("触发器执行异常: nodeKey={}, mode={}, error={}", nodeKey, mode, e.getMessage(), e);
            Object continueOnError = extendConfig.get("continueOnError");
            if (Boolean.TRUE.equals(continueOnError)) {
                log.warn("触发器配置 continueOnError=true, 忽略错误继续流转");
                return finish.apply(execution);
            }
            if (e instanceof FlowLongException) {
                throw (FlowLongException) e;
            }
            throw new FlowLongException("Trigger execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * 业务模式分派
     */
    private boolean dispatch(String mode, Map<String, Object> extendConfig, NodeModel nodeModel, Execution execution) throws Exception {
        switch (mode) {
            case MODE_EXPRESSION:
                return executeExpression(asString(extendConfig.get("triggerExpression")), execution);
            case MODE_BEAN:
                return executeBean(
                        asString(extendConfig.get("triggerBean")),
                        asString(extendConfig.get("triggerMethod")),
                        nodeModel, execution);
            case MODE_CLASS:
                return executeClass(
                        asString(extendConfig.get("triggerClass")),
                        asString(extendConfig.get("triggerMethod")),
                        nodeModel, execution);
            case MODE_DELEGATE_EXPRESSION:
                return executeDelegateExpression(
                        asString(extendConfig.get("triggerExpression")),
                        asString(extendConfig.get("triggerMethod")),
                        nodeModel, execution);
            default:
                log.warn("未知的触发器模式: {}, 视为成功", mode);
                return true;
        }
    }

    /**
     * expression 模式: SpEL 求值
     */
    private boolean executeExpression(String expression, Execution execution) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }
        log.debug("执行 SpEL 表达式: {}", expression);

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(applicationContext));
        Map<String, Object> args = execution.getArgs();
        if (args != null) {
            context.setVariables(args);
        }
        context.setVariable("execution", execution);
        context.setVariable("instance", execution.getFlwInstance());
        context.setVariable("task", execution.getFlwTask());

        try {
            Expression exp = expressionParser.parseExpression(expression);
            Object result = exp.getValue(context);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            log.debug("表达式返回非布尔结果, 视为成功: {}", result);
            return true;
        } catch (BeansException e) {
            log.error("SpEL 调用 Bean 失败: {}, error: {}", expression, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("SpEL 执行失败: {}, error: {}", expression, e.getMessage());
            return false;
        }
    }

    /**
     * bean 模式: 从 Spring 容器拿 Bean,反射调方法
     */
    private boolean executeBean(String beanName, String methodName, NodeModel nodeModel, Execution execution) throws Exception {
        if (beanName == null || beanName.isEmpty()) {
            throw new IllegalArgumentException("bean 模式: triggerBean 不能为空");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("bean 模式: triggerMethod 不能为空");
        }
        TriggerBeanRegistry.TriggerBeanInfo info = triggerBeanRegistry.get(beanName);
        if (info == null) {
            throw new IllegalArgumentException(
                    "bean 模式: 未找到带 @FlowLongTrigger 注解的 Bean '" + beanName + "'");
        }
        Object result = triggerInvoker.invoke(info.getBean(), methodName, nodeModel, execution);
        return interpretResult(result, "bean[" + beanName + "#" + methodName + "]");
    }

    /**
     * class 模式: 反射 .forName().newInstance(),调方法
     */
    private boolean executeClass(String className, String methodName, NodeModel nodeModel, Execution execution) throws Exception {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("class 模式: triggerClass 不能为空");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("class 模式: triggerMethod 不能为空");
        }
        Class<?> clazz = Class.forName(className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Object result = triggerInvoker.invoke(instance, methodName, nodeModel, execution);
        return interpretResult(result, "class[" + className + "#" + methodName + "]");
    }

    /**
     * delegateExpression 模式: SpEL 求出 Bean 名 → 走 bean 路径
     */
    private boolean executeDelegateExpression(String expression, String methodName, NodeModel nodeModel, Execution execution) throws Exception {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("delegateExpression 模式: triggerExpression 不能为空");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("delegateExpression 模式: triggerMethod 不能为空");
        }
        // SpEL 求值
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(applicationContext));
        Map<String, Object> args = execution.getArgs();
        if (args != null) {
            context.setVariables(args);
        }
        context.setVariable("execution", execution);
        context.setVariable("instance", execution.getFlwInstance());

        Object beanNameResult = expressionParser.parseExpression(expression).getValue(context);
        if (beanNameResult == null) {
            throw new IllegalStateException("delegateExpression 求值结果为 null");
        }
        String beanName = beanNameResult.toString();
        TriggerBeanRegistry.TriggerBeanInfo info = triggerBeanRegistry.get(beanName);
        if (info == null) {
            throw new IllegalArgumentException(
                    "delegateExpression: 求值得到 '" + beanName + "', 但该 Bean 未带 @FlowLongTrigger 注解或不存在");
        }
        Object result = triggerInvoker.invoke(info.getBean(), methodName, nodeModel, execution);
        return interpretResult(result, "delegateExpression[" + beanName + "#" + methodName + "]");
    }

    /**
     * 解释方法返回值
     */
    private boolean interpretResult(Object result, String invokeTarget) {
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        // void 或其他返回值: 视为成功
        log.debug("触发器 {} 返回非布尔结果 {}, 视为成功", invokeTarget, result);
        return true;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
