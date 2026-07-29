package com.forge.modules.workflow.framework.trigger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 触发器 Bean 注册表
 *
 * <p>在 {@link ContextRefreshedEvent} 触发时扫描 {@link ApplicationContext} 中所有
 * 带 {@link FlowLongTrigger} 注解的 Bean,缓存到 Map,供设计器 API 列出 +
 * 触发器节点执行时按名查找。</p>
 *
 * <p><b>实现要点:不在 @PostConstruct 阶段扫描</b>。
 * 原因:getBean(beanName) 会强制初始化所有 Bean,若依赖链中存在
 * {@code XXX → TaskTriggerHandler → TriggerBeanRegistry} 的回路,
 * 会在启动期触发 {@code BeanCurrentlyInCreationException}。
 * ContextRefreshedEvent 在所有单例 Bean 都初始化完成后触发,绕开该问题。</p>
 *
 * <p>注意:扫描 AOP 代理 Bean 时需用 {@link AopUtils#getTargetClass} 取真实类判断注解,
 * 否则代理类的注解可能丢失。</p>
 */
@Slf4j
@Component
public class TriggerBeanRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final ApplicationContext applicationContext;

    /**
     * key: bean name (Spring 容器中的 bean 名,小写)
     * value: {name, description, category, bean instance, targetClass}
     */
    private final Map<String, TriggerBeanInfo> registry = new ConcurrentHashMap<>();

    /**
     * 防止父子容器场景下 ContextRefreshedEvent 多次触发时重复扫描
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public TriggerBeanRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 只处理根容器的事件,避免父子容器场景重复触发
        if (event.getApplicationContext().getId() == null
                || !event.getApplicationContext().equals(applicationContext)) {
            // 简化: 只在本容器是发布者时处理
            if (!event.getApplicationContext().equals(applicationContext)) {
                return;
            }
        }
        if (initialized.compareAndSet(false, true)) {
            scan();
        }
    }

    /**
     * 显式触发扫描(保留 API,便于单元测试或运维)
     */
    public void scan() {
        log.info("开始扫描 @FlowLongTrigger Bean ...");
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> targetClass = AopUtils.getTargetClass(bean);
                FlowLongTrigger anno = targetClass.getAnnotation(FlowLongTrigger.class);
                if (anno == null) {
                    continue;
                }
                TriggerBeanInfo info = new TriggerBeanInfo();
                info.setBeanName(beanName);
                info.setClassName(targetClass.getName());
                info.setName(StringUtils.hasText(anno.name()) ? anno.name() : targetClass.getSimpleName());
                info.setDescription(anno.description());
                info.setCategory(anno.category());
                info.setBean(bean);
                info.setTargetClass(targetClass);
                registry.put(beanName, info);
                log.info("注册触发器 Bean: {} ({})", beanName, targetClass.getName());
            } catch (BeansException e) {
                log.warn("扫描 bean {} 失败,跳过", beanName, e);
            } catch (Exception e) {
                log.warn("处理 bean {} 异常,跳过", beanName, e);
            }
        }
        log.info("触发器 Bean 扫描完成,共注册 {} 个", registry.size());
    }

    /**
     * 列出所有注册的触发器 Bean（供前端 API 用）
     */
    public List<TriggerBeanInfo> list() {
        List<TriggerBeanInfo> result = new ArrayList<>(registry.values());
        // 按 category + beanName 排序,稳定输出
        result.sort((a, b) -> {
            int c = a.getCategory().compareTo(b.getCategory());
            return c != 0 ? c : a.getBeanName().compareTo(b.getBeanName());
        });
        return Collections.unmodifiableList(result);
    }

    /**
     * 按 bean name 查找
     *
     * @return TriggerBeanInfo, 不存在返回 null
     */
    public TriggerBeanInfo get(String beanName) {
        if (beanName == null) {
            return null;
        }
        return registry.get(beanName);
    }

    public int size() {
        return registry.size();
    }

    /**
     * 触发器 Bean 信息（不含 method 列表, method 在 UI 上由前端另行提示或由用户输入）
     */
    @lombok.Data
    public static class TriggerBeanInfo {
        private String beanName;
        private String className;
        private String name;
        private String description;
        private String category;
        private Object bean;
        private Class<?> targetClass;
    }
}
