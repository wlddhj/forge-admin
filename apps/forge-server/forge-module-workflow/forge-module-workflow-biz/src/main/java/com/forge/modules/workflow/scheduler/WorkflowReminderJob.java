package com.forge.modules.workflow.scheduler;

import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.model.ProcessModel;
import com.forge.framework.redis.lock.DistributedLock;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流提醒检查定时任务
 *
 * 功能：
 * 1. 检查任务是否需要发送提醒（根据 remindTime 字段）
 * 2. 发送提醒通知（WebSocket、邮件等）
 * 3. 更新提醒次数和下次提醒时间
 *
 * @author forge-admin
 */
@Slf4j
@DisallowConcurrentExecution
public class WorkflowReminderJob implements Job {

    private static final String LOCK_KEY = "workflow_reminder_check";
    private static final FlowCreator SYSTEM_CREATOR = new FlowCreator("SYSTEM", "系统");

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
        NotificationService notificationService = applicationContext.getBean(NotificationService.class);

        // 检查是否启用
        if (!properties.isEnabled()) {
            log.debug("提醒检查已禁用");
            return;
        }

        // 使用分布式锁防止重复执行
        Duration lockTimeout = Duration.ofSeconds(properties.getLockTimeoutSeconds());
        boolean locked = distributedLock.tryLock(LOCK_KEY, lockTimeout);

        if (!locked) {
            log.info("提醒检查任务已被其他实例执行，跳过");
            return;
        }

        try {
            doReminderCheck(taskService, flowLongEngine, notificationService, properties);
        } finally {
            distributedLock.unlock(LOCK_KEY);
        }
    }

    /**
     * 执行提醒检查
     * 使用 FlowLong 内置的 getTimeoutOrRemindTasks 方法获取需要提醒的任务
     */
    private void doReminderCheck(TaskService taskService, FlowLongEngine flowLongEngine,
                                  NotificationService notificationService, FlowLongSchedulerProperties properties) {
        log.info("开始执行提醒检查...");

        // 使用 FlowLong 内置方法获取需要提醒的任务
        // 该方法会查询 remindTime <= 当前时间 的任务
        List<FlwTask> remindTasks = taskService.getTimeoutOrRemindTasks();

        int sentCount = 0;

        for (FlwTask task : remindTasks) {
            // 只处理有 remindTime 的任务（非超时任务）
            if (task.getRemindTime() != null && !isExpired(task)) {
                try {
                    if (sendReminder(task, taskService, flowLongEngine, notificationService, properties)) {
                        sentCount++;
                    }
                } catch (Exception e) {
                    log.error("发送任务提醒失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
                }
            }
        }

        log.info("提醒检查完成: 检查任务数={}, 发送提醒数={}", remindTasks.size(), sentCount);
    }

    /**
     * 检查任务是否已超时（expireTime）
     */
    private boolean isExpired(FlwTask task) {
        Date expireTime = task.getExpireTime();
        if (expireTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(toLocalDateTime(expireTime));
    }

    /**
     * 发送提醒通知
     */
    private boolean sendReminder(FlwTask task, TaskService taskService, FlowLongEngine flowLongEngine,
                                  NotificationService notificationService, FlowLongSchedulerProperties properties) {
        // 获取节点模型和扩展配置
        NodeModel nodeModel = getNodeModel(task, flowLongEngine);
        Map<String, Object> extendConfig = nodeModel != null ? nodeModel.getExtendConfig() : null;

        // 读取节点级别的提醒配置（覆盖全局配置）
        int maxReminderCount = properties.getMaxReminderCount();
        int reminderIntervalHours = properties.getReminderIntervalHours();

        if (extendConfig != null) {
            // 节点级最大提醒次数
            Object maxCountObj = extendConfig.get("remindMaxCount");
            if (maxCountObj instanceof Number) {
                int nodeMaxCount = ((Number) maxCountObj).intValue();
                if (nodeMaxCount > 0) {
                    maxReminderCount = nodeMaxCount;
                }
            }

            // 节点级提醒间隔
            Object intervalObj = extendConfig.get("remindIntervalHours");
            if (intervalObj instanceof Number) {
                int nodeInterval = ((Number) intervalObj).intValue();
                if (nodeInterval > 0) {
                    reminderIntervalHours = nodeInterval;
                }
            }
        }

        // 检查是否已达到最大提醒次数
        Integer remindRepeat = task.getRemindRepeat();
        if (remindRepeat != null && remindRepeat >= maxReminderCount) {
            log.debug("任务已达到最大提醒次数，不再提醒: taskId={}, count={}, max={}",
                    task.getId(), remindRepeat, maxReminderCount);
            return false;
        }

        // 获取任务参与者（使用 QueryService）
        List<FlwTaskActor> actors = flowLongEngine.queryService().getTaskActorsByTaskId(task.getId());
        if (actors == null || actors.isEmpty()) {
            log.warn("任务无参与者，无法发送提醒: taskId={}", task.getId());
            return false;
        }

        // 过滤出用户类型的参与者
        Set<Long> userIds = actors.stream()
                .filter(actor -> actor.getActorType() != null && actor.getActorType() == 0)
                .map(actor -> {
                    try {
                        return Long.parseLong(actor.getActorId());
                    } catch (NumberFormatException e) {
                        log.warn("无法解析参与者ID: {}", actor.getActorId());
                        return null;
                    }
                })
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            log.warn("任务无候选用户，无法发送提醒: taskId={}", task.getId());
            return false;
        }

        // 构建提醒消息
        int repeatCount = remindRepeat != null ? remindRepeat : 0;
        String title = repeatCount >= 1 ? "【紧急】待办任务再次提醒" : "待办任务提醒";
        long elapsedMinutes = calculateElapsedMinutes(task);
        String content = String.format("您有一个待办任务「%s」需要处理，请及时完成。任务已等待 %d 分钟。",
                task.getTaskName(), elapsedMinutes);

        // 发送 WebSocket 通知
        for (Long userId : userIds) {
            try {
                NotificationMessage message = NotificationMessage.workflow(title, content, task.getId());
                notificationService.sendToUser(userId, message);
            } catch (Exception e) {
                log.error("发送WebSocket通知失败: userId={}, taskId={}", userId, task.getId(), e);
            }
        }

        log.info("发送提醒通知: taskId={}, title={}, recipients={}, elapsed={}min",
                task.getId(), title, userIds.size(), elapsedMinutes);

        // 更新提醒次数和下次提醒时间（使用节点级或全局配置）
        updateRemindCount(task, taskService, maxReminderCount, reminderIntervalHours);

        return true;
    }

    /**
     * 获取任务的节点模型
     */
    private NodeModel getNodeModel(FlwTask task, FlowLongEngine flowLongEngine) {
        try {
            ProcessModel processModel = flowLongEngine.runtimeService()
                    .getProcessModelByInstanceId(task.getInstanceId());
            if (processModel != null) {
                return processModel.getNode(task.getTaskKey());
            }
        } catch (Exception e) {
            log.warn("获取节点模型失败: taskId={}, taskKey={}", task.getId(), task.getTaskKey(), e);
        }
        return null;
    }

    /**
     * 更新提醒次数和下次提醒时间
     */
    private void updateRemindCount(FlwTask task, TaskService taskService,
                                    int maxReminderCount, int reminderIntervalHours) {
        try {
            FlwTask updateTask = new FlwTask();
            updateTask.setId(task.getId());

            Integer remindRepeat = task.getRemindRepeat();
            int newRepeat = (remindRepeat != null ? remindRepeat : 0) + 1;
            updateTask.setRemindRepeat(newRepeat);

            // 设置下次提醒时间（使用节点级或全局配置的间隔）
            if (newRepeat < maxReminderCount) {
                LocalDateTime nextRemindTime = LocalDateTime.now()
                        .plusHours(reminderIntervalHours);
                updateTask.setRemindTime(Date.from(nextRemindTime.atZone(ZoneId.systemDefault()).toInstant()));
                log.debug("设置下次提醒时间: taskId={}, nextRemindTime={}, intervalHours={}",
                        task.getId(), nextRemindTime, reminderIntervalHours);
            } else {
                // 达到最大次数后不再设置下次提醒时间
                updateTask.setRemindTime(null);
                log.info("任务达到最大提醒次数，不再设置下次提醒: taskId={}, count={}, max={}",
                        task.getId(), newRepeat, maxReminderCount);
            }

            // 更新任务
            taskService.updateTaskById(updateTask, SYSTEM_CREATOR);

            log.debug("更新提醒次数: taskId={}, newRepeat={}", task.getId(), newRepeat);
        } catch (Exception e) {
            log.error("更新提醒次数失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private long calculateElapsedMinutes(FlwTask task) {
        LocalDateTime createTime = toLocalDateTime(task.getCreateTime());
        return Duration.between(createTime, LocalDateTime.now()).toMinutes();
    }
}