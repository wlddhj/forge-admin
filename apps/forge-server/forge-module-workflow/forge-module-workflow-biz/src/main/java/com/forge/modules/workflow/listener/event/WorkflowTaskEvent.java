package com.forge.modules.workflow.listener.event;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;
import java.util.List;

/**
 * 任务事件 - Spring 事件机制
 * 用于解耦任务生命周期事件的处理
 *
 * @author forge-admin
 */
@Getter
public class WorkflowTaskEvent extends ApplicationEvent implements Serializable {

    /**
     * 事件类型
     */
    private final TaskEventType eventType;

    /**
     * 任务
     */
    private final FlwTask task;

    /**
     * 任务参与者列表
     */
    private final List<FlwTaskActor> taskActors;

    /**
     * 当前执行节点
     */
    private final NodeModel nodeModel;

    /**
     * 流程创建者/操作者
     */
    private final FlowCreator flowCreator;

    public WorkflowTaskEvent(Object source, TaskEventType eventType, FlwTask task,
                              List<FlwTaskActor> taskActors, NodeModel nodeModel, FlowCreator flowCreator) {
        super(source);
        this.eventType = eventType;
        this.task = task;
        this.taskActors = taskActors;
        this.nodeModel = nodeModel;
        this.flowCreator = flowCreator;
    }

    /**
     * 获取任务ID
     */
    public Long getTaskId() {
        return task != null ? task.getId() : null;
    }

    /**
     * 获取任务名称
     */
    public String getTaskName() {
        return task != null ? task.getTaskName() : null;
    }

    /**
     * 获取任务Key
     */
    public String getTaskKey() {
        return task != null ? task.getTaskKey() : null;
    }

    /**
     * 获取流程实例ID
     */
    public Long getInstanceId() {
        return task != null ? task.getInstanceId() : null;
    }

    /**
     * 获取操作者ID
     */
    public String getOperatorId() {
        return flowCreator != null ? flowCreator.getCreateId() : null;
    }

    /**
     * 获取操作者名称
     */
    public String getOperatorName() {
        return flowCreator != null ? flowCreator.getCreateBy() : null;
    }
}