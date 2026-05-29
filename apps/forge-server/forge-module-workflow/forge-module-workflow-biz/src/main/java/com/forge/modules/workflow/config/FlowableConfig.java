package com.forge.modules.workflow.config;

import com.forge.modules.workflow.framework.listener.GlobalProcessCompletedEventListener;
import com.forge.modules.workflow.framework.listener.GlobalTaskCreatedEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
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
    private final GlobalProcessCompletedEventListener globalProcessCompletedEventListener;

    public FlowableConfig(GlobalTaskCreatedEventListener globalTaskCreatedEventListener,
                          GlobalProcessCompletedEventListener globalProcessCompletedEventListener) {
        this.globalTaskCreatedEventListener = globalTaskCreatedEventListener;
        this.globalProcessCompletedEventListener = globalProcessCompletedEventListener;
    }

    @Override
    public void configure(SpringProcessEngineConfiguration config) {
        // 流程图字体设置，防止中文乱码
        config.setActivityFontName("宋体");
        config.setLabelFontName("宋体");
        config.setAnnotationFontName("宋体");

        // 注册全局事件监听器
        Map<String, List<FlowableEventListener>> typedListeners = new HashMap<>();
        typedListeners.put("TASK_CREATED", Collections.singletonList(globalTaskCreatedEventListener));
        typedListeners.put("PROCESS_COMPLETED", Collections.singletonList(globalProcessCompletedEventListener));
        config.setTypedEventListeners(typedListeners);
    }
}
