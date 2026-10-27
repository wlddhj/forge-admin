package com.forge.modules.workflow.callback;

import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 回调负载构建器：工作流事件 → 稳定契约 JSON
 * <p>
 * 契约：{"eventType","instanceId","processKey","processName","businessKey","taskId",
 * "taskName","nodeKey","operatorId","operatorName","instanceState"(仅终态),"variables"(可选),"timestamp"}
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class CallbackPayloadBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 任务类事件负载
     */
    public String build(CallbackEventType eventType, WorkflowTaskEvent event, String processKey) {
        Map<String, Object> payload = base(eventType, processKey, event.getOperatorId(), event.getOperatorName());
        FlwTask task = event.getTask();
        if (task != null) {
            payload.put("instanceId", task.getInstanceId());
            payload.put("taskId", task.getId());
            payload.put("taskName", task.getTaskName());
            payload.put("nodeKey", task.getTaskKey());
            payload.put("businessKey", null);
        }
        return write(payload);
    }

    /**
     * 实例终态负载；includeVariables 开启时附流程变量
     */
    public String build(WorkflowInstanceEvent event, String processKey, String processName,
                        boolean includeVariables) {
        Map<String, Object> payload = base(CallbackEventType.instanceFinished, processKey,
                event.getOperatorId(), event.getOperatorName());
        payload.put("processName", processName);
        FlwHisInstance instance = event.getInstance();
        if (instance != null) {
            payload.put("instanceId", instance.getId());
            payload.put("businessKey", instance.getBusinessKey());
            payload.put("instanceState", instance.getInstanceState());
            if (includeVariables && instance.getVariable() != null) {
                payload.put("variables", readVariables(instance.getVariable()));
            }
        }
        return write(payload);
    }

    private Map<String, Object> base(CallbackEventType eventType, String processKey,
                                     String operatorId, String operatorName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType.name());
        payload.put("processKey", processKey);
        payload.put("operatorId", operatorId);
        payload.put("operatorName", operatorName);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private Map<String, Object> readVariables(String variableJson) {
        try {
            return objectMapper.readValue(variableJson, Map.class);
        } catch (Exception e) {
            return Map.of("_raw", variableJson);
        }
    }

    private String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("回调负载序列化失败", e);
        }
    }
}
