package com.forge.modules.workflow.scheduler;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.forge.framework.redis.lock.DistributedLock;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.framework.reminder.TaskReminderHandler;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 工作流超时检查定时任务
 *
 * 功能：
 * 1. 检查任务是否超时（根据节点配置的 term 值）
 * 2. 执行自动处理（自动通过或自动拒绝）
 * 3. 发送超时提醒通知
 *
 * @author forge-admin
 */
@Slf4j
@DisallowConcurrentExecution
public class WorkflowTimeoutJob implements Job {

    private static final String LOCK_KEY = "workflow_timeout_check";
    private static final FlowCreator SYSTEM_CREATOR = new FlowCreator("SYSTEM", "系统自动处理");

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // 获取 ApplicationContext
        SchedulerContext schedulerContext;
        try {
            schedulerContext = context.getScheduler().getContext();
        } catch (SchedulerException e) {
            log.error("获取 SchedulerContext 失败", e);
            return;
        }

        ApplicationContext applicationContext = (ApplicationContext) schedulerContext.get("applicationContextKey");
        if (applicationContext == null) {
            log.error("ApplicationContext 未设置");
            return;
        }

        // 获取依赖组件
        DistributedLock distributedLock = applicationContext.getBean(DistributedLock.class);
        FlowLongSchedulerProperties properties = applicationContext.getBean(FlowLongSchedulerProperties.class);
        FlowLongEngine flowLongEngine = applicationContext.getBean(FlowLongEngine.class);
        TaskService taskService = flowLongEngine.taskService();
        TaskReminderHandler reminderHandler = applicationContext.getBean(TaskReminderHandler.class);

        // 检查是否启用
        if (!properties.isEnabled()) {
            log.debug("超时检查已禁用");
            return;
        }

        // 使用分布式锁防止重复执行
        Duration lockTimeout = Duration.ofSeconds(properties.getLockTimeoutSeconds());
        boolean locked = distributedLock.tryLock(LOCK_KEY, lockTimeout);

        if (!locked) {
            log.info("超时检查任务已被其他实例执行，跳过");
            return;
        }

        try {
            doTimeoutCheck(taskService, flowLongEngine, reminderHandler, properties);
        } finally {
            distributedLock.unlock(LOCK_KEY);
        }
    }

    /**
     * 执行超时检查
     */
    private void doTimeoutCheck(TaskService taskService,
                                 FlowLongEngine flowLongEngine, TaskReminderHandler reminderHandler,
                                 FlowLongSchedulerProperties properties) {
        log.info("开始执行超时检查...");

        // 使用 FlowLong 内置方法获取超时或需要提醒的任务
        // 该方法会查询 expireTime <= 当前时间 或 remindTime <= 当前时间的任务
        List<FlwTask> timeoutTasks = taskService.getTimeoutOrRemindTasks();

        int processedCount = 0;

        for (FlwTask task : timeoutTasks) {
            try {
                if (checkAndProcessTimeout(task, flowLongEngine, taskService, reminderHandler, properties)) {
                    processedCount++;
                }
            } catch (Exception e) {
                log.error("处理任务超时失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
            }
        }

        log.info("超时检查完成: 检查任务数={}, 处理数={}", timeoutTasks.size(), processedCount);
    }

    /**
     * 检查并处理超时任务
     */
    private boolean checkAndProcessTimeout(FlwTask task, FlowLongEngine flowLongEngine,
                                            TaskService taskService, TaskReminderHandler reminderHandler,
                                            FlowLongSchedulerProperties properties) {
        // 检查是否是定时器或触发器任务（直接完成）
        if (TaskType.timer.eq(task.getTaskType()) || TaskType.trigger.eq(task.getTaskType())) {
            log.info("定时器/触发器任务超时，自动完成: taskId={}", task.getId());
            return flowLongEngine.autoCompleteTask(task.getId(), SYSTEM_CREATOR);
        }

        // 获取节点配置
        NodeModel nodeModel = getNodeModel(task, flowLongEngine);
        if (nodeModel == null) {
            log.warn("无法获取节点模型: taskId={}, taskKey={}", task.getId(), task.getTaskKey());
            return false;
        }

        // 检查是否配置了超时自动处理
        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        if (extendConfig == null) {
            return false;
        }

        Boolean termAuto = getBoolean(extendConfig, "termAuto");
        if (!Boolean.TRUE.equals(termAuto)) {
            return false;
        }

        // 检查是否已经超时
        LocalDateTime createTime = toLocalDateTime(task.getCreateTime());
        LocalDateTime now = LocalDateTime.now();
        Integer termValue = getInteger(extendConfig, "termValue"); // 超时时长（分钟）

        if (termValue == null || termValue <= 0) {
            return false;
        }

        long elapsedMinutes = Duration.between(createTime, now).toMinutes();
        if (elapsedMinutes < termValue) {
            log.debug("任务未超时: taskId={}, elapsed={}min, limit={}min",
                    task.getId(), elapsedMinutes, termValue);
            return false;
        }

        log.info("任务已超时: taskId={}, elapsed={}min, limit={}min",
                task.getId(), elapsedMinutes, termValue);

        // 发送超时提醒
        reminderHandler.sendTimeoutReminder(task);

        // 执行自动处理
        Integer termMode = getInteger(extendConfig, "termMode");
        if (termMode == null) {
            termMode = 0; // 默认自动通过
        }

        boolean success;
        if (termMode == 0) {
            log.info("自动通过超时任务: taskId={}", task.getId());
            success = flowLongEngine.autoCompleteTask(task.getId(), SYSTEM_CREATOR);
        } else {
            log.info("自动拒绝超时任务: taskId={}", task.getId());
            success = flowLongEngine.autoRejectTask(task, SYSTEM_CREATOR);
        }

        return success;
    }

    /**
     * 获取节点模型
     */
    private NodeModel getNodeModel(FlwTask task, FlowLongEngine flowLongEngine) {
        try {
            ProcessModel processModel = flowLongEngine.runtimeService()
                    .getProcessModelByInstanceId(task.getInstanceId());
            if (processModel != null) {
                return processModel.getNode(task.getTaskKey());
            }
        } catch (Exception e) {
            log.error("获取流程模型失败: instanceId={}", task.getInstanceId(), e);
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}