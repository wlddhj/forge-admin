package com.forge.modules.workflow.listener;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.spring.event.EventTaskListener;
import com.forge.modules.workflow.framework.ai.AiApprovalExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 复合任务监听器 - 继承 EventTaskListener
 *
 * 合并多个 TaskListener 的功能：
 * 1. EventTaskListener - 发布 FlowLong TaskEvent
 * 2. BpmTaskCandidateListener - 候选人自动分配
 * 3. TaskNotificationListener - 通知推送
 * 4. AiApprovalExecutor - AI智能审批
 *
 * @author forge-admin
 */
@Slf4j
@Component("taskListener")
public class CompositeTaskListener extends EventTaskListener {

    private static final FlowCreator SYSTEM_CREATOR = new FlowCreator("SYSTEM", "系统");

    private final BpmTaskCandidateListener candidateListener;
    private final TaskNotificationListener notificationListener;
    private final AiApprovalExecutor aiApprovalExecutor;
    private final FlowLongEngine flowLongEngine;

    public CompositeTaskListener(ApplicationEventPublisher eventPublisher,
                                  BpmTaskCandidateListener candidateListener,
                                  TaskNotificationListener notificationListener,
                                  AiApprovalExecutor aiApprovalExecutor,
                                  FlowLongEngine flowLongEngine) {
        super(eventPublisher);
        this.candidateListener = candidateListener;
        this.notificationListener = notificationListener;
        this.aiApprovalExecutor = aiApprovalExecutor;
        this.flowLongEngine = flowLongEngine;
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

        // 2. 任务创建时设置提醒时间（基于节点配置）
        if (eventType == TaskEventType.create && nodeModel != null) {
            setRemindTime(task, nodeModel, flowLongEngine.taskService());
        }

        // 3. 调用候选人监听器处理候选人分配
        try {
            candidateListener.notify(eventType, supplier, taskActors, nodeModel, flowCreator);
        } catch (Exception e) {
            log.error("候选人监听器处理失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }

        // 4. 调用通知监听器发送通知
        try {
            notificationListener.notify(eventType, supplier, taskActors, nodeModel, flowCreator);
        } catch (Exception e) {
            log.error("通知监听器处理失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
        }

        // 5. 任务创建时检查并执行AI审批
        if (eventType == TaskEventType.create && nodeModel != null) {
            try {
                boolean aiExecuted = aiApprovalExecutor.checkAndExecuteAiApproval(task, nodeModel, flowCreator);
                if (aiExecuted) {
                    log.info("AI审批已自动执行: taskId={}, taskName={}", task.getId(), task.getTaskName());
                }
            } catch (Exception e) {
                log.error("AI审批执行失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
            }
        }

        // 返回 false 表示不干预流程执行
        return false;
    }

    /**
     * 根据节点配置设置任务提醒时间
     */
    private void setRemindTime(FlwTask task, NodeModel nodeModel, TaskService taskService) {
        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        if (extendConfig == null) {
            return;
        }

        // 读取提醒配置
        Object remindAutoObj = extendConfig.get("remindAuto");
        if (remindAutoObj == null || !Boolean.TRUE.equals(remindAutoObj)) {
            log.debug("节点未启用超时提醒: nodeId={}", nodeModel.getNodeKey());
            return;
        }

        // 读取提前提醒时间（分钟）
        Object remindAdvanceObj = extendConfig.get("remindAdvanceMinutes");
        if (remindAdvanceObj == null) {
            log.debug("节点未配置提前提醒时间: nodeId={}", nodeModel.getNodeKey());
            return;
        }

        int remindAdvanceMinutes = 0;
        if (remindAdvanceObj instanceof Number) {
            remindAdvanceMinutes = ((Number) remindAdvanceObj).intValue();
        } else if (remindAdvanceObj instanceof String) {
            try {
                remindAdvanceMinutes = Integer.parseInt((String) remindAdvanceObj);
            } catch (NumberFormatException e) {
                log.warn("无法解析提前提醒时间: {}", remindAdvanceObj);
                return;
            }
        }

        if (remindAdvanceMinutes <= 0) {
            log.debug("提前提醒时间无效，跳过设置: nodeId={}, value={}", nodeModel.getNodeKey(), remindAdvanceMinutes);
            return;
        }

        // 计算提醒时间（创建时间 + 提前提醒分钟）
        LocalDateTime createTime = LocalDateTime.now();
        LocalDateTime remindTime = createTime.plusMinutes(remindAdvanceMinutes);

        // 更新任务
        FlwTask updateTask = new FlwTask();
        updateTask.setId(task.getId());
        updateTask.setRemindTime(Date.from(remindTime.atZone(ZoneId.systemDefault()).toInstant()));

        try {
            taskService.updateTaskById(updateTask, SYSTEM_CREATOR);
            log.info("设置任务提醒时间: taskId={}, remindTime={}, advanceMinutes={}",
                    task.getId(), remindTime, remindAdvanceMinutes);
        } catch (Exception e) {
            log.error("设置任务提醒时间失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }
}