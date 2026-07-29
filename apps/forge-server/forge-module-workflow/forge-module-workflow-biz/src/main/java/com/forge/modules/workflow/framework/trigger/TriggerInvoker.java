package com.forge.modules.workflow.framework.trigger;

import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.model.NodeModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 触发器方法反射调用器
 *
 * <p>负责在运行时调用 {@code @FlowLongTrigger} 标注的 Bean 的指定方法。</p>
 *
 * <p>调用优先级:
 * <ol>
 *   <li>如果 target 是 {@link AbstractFlowLongTrigger} 子类,且 methodName = "execute",
 *       优先直接调 {@code target.execute(execution, nodeModel, ctx)}（避免反射 + 缓存 Method）</li>
 *   <li>否则走反射路径（用于灵活签名 + {@link TriggerParam} 注入）</li>
 * </ol>
 * </p>
 *
 * <p>反射路径的形参解析规则（按优先级匹配）:
 * <ol>
 *   <li>标注 {@link TriggerParam} 的形参 — 按注解 value 从 ctx 取值,支持基本类型自动转换</li>
 *   <li>类型为 {@link Execution} 的形参 — 注入 execution</li>
 *   <li>类型为 {@link NodeModel} 的形参 — 注入 nodeModel</li>
 *   <li>类型为 Map 的形参 — 注入整个 ctx</li>
 *   <li>其他未标注形参 — 注入 null</li>
 * </ol>
 * </p>
 *
 * <p>返回值约定:
 * <ul>
 *   <li>{@code boolean} — 直接作为触发结果</li>
 *   <li>{@code void} — 视为成功</li>
 *   <li>其他 — 视为成功, 丢弃返回值</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class TriggerInvoker {

    /**
     * 调用指定对象的指定方法
     *
     * @param target     目标对象（Bean 实例或 class.newInstance()）
     * @param methodName 方法名
     * @param nodeModel  FlowLong 节点模型
     * @param execution  FlowLong 执行上下文
     * @return 方法返回值（boolean / void / 其他）
     * @throws Exception 反射调用失败
     */
    public Object invoke(Object target, String methodName, NodeModel nodeModel, Execution execution)
            throws Exception {
        if (target == null) {
            throw new IllegalArgumentException("触发器目标对象为 null");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("触发器方法名不能为空");
        }

        // 优先级 1: AbstractFlowLongTrigger 子类 + 方法名 execute → 直接调基类约定
        if ("execute".equals(methodName) && target instanceof AbstractFlowLongTrigger) {
            Map<String, Object> ctx = execution.getArgs();
            try {
                AbstractFlowLongTrigger trigger = (AbstractFlowLongTrigger) target;
                return trigger.execute(execution, nodeModel, ctx);
            } catch (Exception e) {
                throw e; // 用户代码异常原样抛出,不拆包
            }
        }

        // 优先级 2: 反射路径（灵活签名 / @TriggerParam）
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) {
            throw new NoSuchMethodException(
                    "在 " + target.getClass().getName() + " 中找不到方法 " + methodName);
        }

        // 构造 ctx: 流程变量
        Map<String, Object> ctx = execution.getArgs();
        if (ctx == null) {
            ctx = new HashMap<>();
        }

        Object[] args = resolveArgs(method, nodeModel, execution, ctx);
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            // 拆包: 用户代码抛出的异常应当原样抛出
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    /**
     * 在类中查找指定名称的方法（不严格匹配形参, 由 resolveArgs 阶段再校验）
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() > 0) {
                return m;
            }
        }
        // 也查 declared methods(public + private)
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() > 0) {
                return m;
            }
        }
        return null;
    }

    /**
     * 解析方法形参,按规则注入
     */
    private Object[] resolveArgs(Method method, NodeModel nodeModel, Execution execution, Map<String, Object> ctx) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            TriggerParam annotation = p.getAnnotation(TriggerParam.class);
            if (annotation != null) {
                // 按 @TriggerParam 从 ctx 注入
                Object value = ctx.get(annotation.value());
                if (value == null && annotation.required()) {
                    throw new IllegalArgumentException(
                            "触发器方法 " + method.getName() + " 的参数 '" + annotation.value() + "' 必填但 ctx 中缺失");
                }
                args[i] = convert(value, p.getType());
            } else if (Execution.class.isAssignableFrom(p.getType())) {
                args[i] = execution;
            } else if (NodeModel.class.isAssignableFrom(p.getType())) {
                args[i] = nodeModel;
            } else if (Map.class.isAssignableFrom(p.getType())) {
                args[i] = ctx;
            } else {
                log.debug("触发器方法 {} 的形参 {} 未标注 @TriggerParam, 注入 null", method.getName(), p.getName());
                args[i] = null;
            }
        }
        return args;
    }

    /**
     * 类型转换: ctx 中的 String/Number 等转为目标形参类型
     * 失败时回退为原值（让用户代码自己处理类型）
     */
    private Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        // 基本类型转换
        try {
            if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(value.toString());
            }
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(value.toString());
            }
            if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(value.toString());
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(value.toString());
            }
            if (targetType == String.class) {
                return value.toString();
            }
        } catch (Exception e) {
            log.warn("类型转换失败: value={} → {}, 保持原值", value, targetType.getName());
        }
        return value;
    }
}
