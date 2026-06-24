package com.forge.modules.workflow.framework.trigger;

import com.aizuda.bpm.engine.TaskTrigger;
import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.model.NodeModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/**
 * 任务触发器处理器
 * 处理触发器类型节点的自定义业务逻辑
 *
 * 触发器类型：
 * - expression: 执行 SpEL 表达式
 * - service: 调用指定服务方法
 * - message: 发送消息触发后续流程
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class TaskTriggerHandler implements TaskTrigger {

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Override
    public boolean execute(NodeModel nodeModel, Execution execution, Function<Execution, Boolean> finish) {
        log.info("执行触发器: nodeKey={}, nodeName={}",
                nodeModel.getNodeKey(), nodeModel.getNodeName());

        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        if (extendConfig == null) {
            log.warn("触发器节点 {} 未配置扩展信息，直接完成", nodeModel.getNodeKey());
            return finish.apply(execution);
        }

        String triggerType = (String) extendConfig.get("triggerType");
        String triggerExpression = (String) extendConfig.get("triggerExpression");

        try {
            boolean success = executeTrigger(triggerType, triggerExpression, execution);
            if (success) {
                log.info("触发器执行成功: nodeKey={}, type={}", nodeModel.getNodeKey(), triggerType);
                return finish.apply(execution);
            } else {
                log.warn("触发器执行失败，流程暂停: nodeKey={}", nodeModel.getNodeKey());
                return false;
            }
        } catch (Exception e) {
            log.error("触发器执行异常: nodeKey={}, error={}", nodeModel.getNodeKey(), e.getMessage(), e);
            // 根据配置决定是否继续流程
            Object continueOnError = extendConfig.get("continueOnError");
            if (Boolean.TRUE.equals(continueOnError)) {
                log.warn("触发器配置了继续执行，忽略错误");
                return finish.apply(execution);
            }
            throw new com.aizuda.bpm.engine.exception.FlowLongException("Trigger execution failed: " + e.getMessage());
        }
    }

    /**
     * 执行触发器逻辑
     */
    private boolean executeTrigger(String triggerType, String expression, Execution execution) {
        if (triggerType == null) {
            triggerType = "expression";
        }

        switch (triggerType) {
            case "expression":
                return executeExpressionTrigger(expression, execution);
            case "service":
                return executeServiceTrigger(expression, execution);
            case "message":
                return executeMessageTrigger(expression, execution);
            case "http":
                return executeHttpTrigger(expression, execution);
            default:
                log.warn("未知的触发器类型: {}", triggerType);
                return true;
        }
    }

    /**
     * 执行表达式触发器（SpEL）
     */
    private boolean executeExpressionTrigger(String expression, Execution execution) {
        if (expression == null || expression.isEmpty()) {
            return true;
        }

        log.debug("执行 SpEL 表达式: {}", expression);

        StandardEvaluationContext context = new StandardEvaluationContext();
        // 设置流程变量作为上下文
        Map<String, Object> args = execution.getArgs();
        if (args != null) {
            context.setVariables(args);
        }
        // 设置执行对象
        context.setVariable("execution", execution);
        context.setVariable("instance", execution.getFlwInstance());

        try {
            Object result = expressionParser.parseExpression(expression).getValue(context);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            log.debug("表达式返回结果: {}", result);
            return true;
        } catch (Exception e) {
            log.error("SpEL 表达式执行失败: {}, error: {}", expression, e.getMessage());
            return false;
        }
    }

    /**
     * 执行服务触发器
     * 格式：serviceName.methodName 或 beanName.methodName
     */
    private boolean executeServiceTrigger(String serviceName, Execution execution) {
        if (serviceName == null || serviceName.isEmpty()) {
            return true;
        }

        log.debug("执行服务触发器: {}", serviceName);

        try {
            // 解析服务名和方法名
            String[] parts = serviceName.split("\\.");
            if (parts.length != 2) {
                log.warn("服务名称格式错误，应为 serviceName.methodName: {}", serviceName);
                return true;
            }

            // TODO: 实现服务调用逻辑
            // 可以通过 ApplicationContext 获取 Bean 并调用方法
            log.info("调用服务: bean={}, method={}", parts[0], parts[1]);

            return true;
        } catch (Exception e) {
            log.error("服务触发器执行失败: {}, error: {}", serviceName, e.getMessage());
            return false;
        }
    }

    /**
     * 执行消息触发器
     * 发送消息到消息队列或事件总线
     */
    private boolean executeMessageTrigger(String messageKey, Execution execution) {
        if (messageKey == null || messageKey.isEmpty()) {
            return true;
        }

        log.debug("执行消息触发器: {}", messageKey);

        // TODO: 实现消息发送逻辑
        // 可以集成消息队列（如 RabbitMQ、Kafka）或事件总线

        Map<String, Object> messageData = new java.util.HashMap<>();
        messageData.put("instanceId", execution.getFlwInstance() != null ? execution.getFlwInstance().getId() : null);
        NodeModel execNodeModel = execution.getProcessModel() != null && execution.getFlwTask() != null
                ? execution.getProcessModel().getNode(execution.getFlwTask().getTaskKey()) : null;
        messageData.put("nodeKey", execNodeModel != null ? execNodeModel.getNodeKey() : null);
        messageData.put("variables", execution.getArgs());

        log.info("发送消息: key={}, data={}", messageKey, messageData);

        return true;
    }

    /**
     * 执行 HTTP 触发器
     * 调用外部 HTTP 接口
     */
    private boolean executeHttpTrigger(String url, Execution execution) {
        if (url == null || url.isEmpty()) {
            return true;
        }

        log.debug("执行 HTTP 触发器: {}", url);

        // TODO: 实现 HTTP 调用逻辑
        // 可以使用 RestTemplate 或 WebClient

        log.info("调用 HTTP 接口: {}", url);

        return true;
    }
}