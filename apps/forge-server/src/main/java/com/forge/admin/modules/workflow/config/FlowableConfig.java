package com.forge.admin.modules.workflow.config;

import com.forge.admin.modules.workflow.framework.listener.GlobalTaskCreatedEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 工作流引擎配置
 */
@Configuration
public class FlowableConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

    private final GlobalTaskCreatedEventListener globalTaskCreatedEventListener;

    public FlowableConfig(GlobalTaskCreatedEventListener globalTaskCreatedEventListener) {
        this.globalTaskCreatedEventListener = globalTaskCreatedEventListener;
    }

    @Override
    public void configure(SpringProcessEngineConfiguration config) {
        // 流程图字体设置，防止中文乱码
        config.setActivityFontName("宋体");
        config.setLabelFontName("宋体");
        config.setAnnotationFontName("宋体");

        // 注册全局任务创建事件监听器，自动分配候选人
        // 使用 typedEventListeners 按 event type 注册监听器
        Map<String, List<FlowableEventListener>> typedListeners = new HashMap<>();
        typedListeners.put("TASK_CREATED", Collections.singletonList(globalTaskCreatedEventListener));
        config.setTypedEventListeners(typedListeners);
    }
}
