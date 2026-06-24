package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.spring.event.EventTaskListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 复合任务监听器 - 继承 EventTaskListener
 *
 * 合并多个 TaskListener 的功能：
 * 1. EventTaskListener - 发布 FlowLong TaskEvent
 * 2. BpmTaskCandidateListener - 候选人自动分配
 * 3. TaskNotificationListener - 通知推送
 *
 * @author forge-admin
 */
@Slf4j
@Component("taskListener")
public class CompositeTaskListener extends EventTaskListener {

    private final BpmTaskCandidateListener candidateListener;
    private final TaskNotificationListener notificationListener;

    public CompositeTaskListener(ApplicationEventPublisher eventPublisher,
                                  BpmTaskCandidateListener candidateListener,
                                  TaskNotificationListener notificationListener) {
        super(eventPublisher);
        this.candidateListener = candidateListener;
        this.notificationListener = notificationListener;
    }

    @Override
    public boolean notify(TaskEventType eventType, Supplier<FlwTask> supplier,
                          List<FlwTaskActor> taskActors, NodeModel nodeModel, FlowCreator flowCreator) {
        // 1. 先调用父类方法，发布 FlowLong 的 TaskEvent
        super.notify(eventType, supplier, taskActors, nodeModel, flowCreator);

        FlwTask task = supplier.get();
        if (task == null) {
            log.warn("任务事件监听: 任务为空, eventType={}", eventType);
            return false;
        }

        log.debug("任务事件: type={}, taskId={}, taskName={}, operator={}",
                eventType.name(), task.getId(), task.getTaskName(),
                flowCreator != null ? flowCreator.getCreateBy() : "unknown");

        // 2. 调用候选人监听器处理候选人分配
        try {
            candidateListener.notify(eventType, supplier, taskActors, nodeModel, flowCreator);
        } catch (Exception e) {
            log.error("候选人监听器处理失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }

        // 3. 调用通知监听器发送通知
        try {
            notificationListener.notify(eventType, supplier, taskActors, nodeModel, flowCreator);
        } catch (Exception e) {
            log.error("通知监听器处理失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }

        // 返回 false 表示不干预流程执行
        return false;
    }
}