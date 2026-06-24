package com.forge.modules.workflow.framework.reminder;

import com.aizuda.bpm.engine.TaskReminder;
import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.FlowLongContext;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务提醒处理器
 * 集成 WebSocket 和邮件通知
 *
 * 提醒渠道：
 * - WebSocket: 实时推送通知
 * - Email: 发送提醒邮件（可选）
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class TaskReminderHandler implements TaskReminder {

    private final NotificationService notificationService;
    private final FlowLongEngine flowLongEngine;

    /**
     * 默认下次提醒间隔（小时）
     */
    private static final int DEFAULT_REMIND_INTERVAL_HOURS = 24;

    /**
     * 系统创建者（用于更新任务）
     */
    private static final FlowCreator SYSTEM_CREATOR = new FlowCreator("SYSTEM", "系统");

    public TaskReminderHandler(NotificationService notificationService, FlowLongEngine flowLongEngine) {
        this.notificationService = notificationService;
        this.flowLongEngine = flowLongEngine;
    }

    /**
     * 获取 QueryService
     */
    private QueryService getQueryService() {
        return flowLongEngine.queryService();
    }

    /**
     * 获取 TaskService
     */
    private com.aizuda.bpm.engine.TaskService getTaskService() {
        return flowLongEngine.taskService();
    }

    @Override
    public Date remind(FlowLongContext context, Long instanceId, FlwTask currentTask) {
        log.info("发送任务提醒: taskId={}, taskName={}", currentTask.getId(), currentTask.getTaskName());

        // 获取任务参与者
        List<FlwTaskActor> actors = getQueryService().getTaskActorsByTaskId(currentTask.getId());
        if (actors == null || actors.isEmpty()) {
            log.warn("任务 {} 无参与者，无法发送提醒", currentTask.getId());
            return null; // 不再提醒
        }

        // 过滤出用户类型的参与者
        Set<Long> userIds = actors.stream()
                .filter(actor -> actor.getActorType() != null && actor.getActorType() == 0) // 用户类型
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
            log.warn("任务 {} 无候选用户，无法发送提醒", currentTask.getId());
            return null;
        }

        // 发送 WebSocket 通知
        sendWebSocketReminder(currentTask, userIds);

        // 可选：发送邮件提醒
        sendEmailReminder(currentTask, userIds);

        // 计算下次提醒时间
        return calculateNextRemindTime(currentTask);
    }

    /**
     * 发送 WebSocket 提醒通知
     */
    private void sendWebSocketReminder(FlwTask task, Set<Long> userIds) {
        String title = "待办任务提醒";
        String content = String.format("您有一个待办任务「%s」需要处理，请及时完成。", task.getTaskName());

        for (Long userId : userIds) {
            try {
                NotificationMessage message = NotificationMessage.workflow(title, content, task.getId());
                notificationService.sendToUser(userId, message);
            } catch (Exception e) {
                log.error("发送WebSocket通知失败: userId={}, taskId={}", userId, task.getId(), e);
            }
        }

        log.info("WebSocket 提醒发送完成: taskId={}, recipients={}", task.getId(), userIds);
    }

    /**
     * 发送邮件提醒（可选）
     */
    private void sendEmailReminder(FlwTask task, Set<Long> userIds) {
        // TODO: 集成邮件服务
        // 根据配置决定是否发送邮件
        log.debug("邮件提醒准备发送: taskId={}, recipients={}", task.getId(), userIds);
    }

    /**
     * 计算下次提醒时间
     */
    private Date calculateNextRemindTime(FlwTask task) {
        // 获取任务的超时配置
        Integer remindRepeat = task.getRemindRepeat();
        int maxRemindCount = 3; // 默认最大提醒次数

        // 如果已达到最大提醒次数，不再提醒
        if (remindRepeat != null && remindRepeat >= maxRemindCount) {
            log.info("任务 {} 已达到最大提醒次数，不再提醒", task.getId());
            return null;
        }

        // 计算下次提醒时间（默认 24 小时后）
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, DEFAULT_REMIND_INTERVAL_HOURS);
        return calendar.getTime();
    }

    /**
     * 发送超时提醒（紧急）
     */
    public void sendTimeoutReminder(FlwTask task) {
        log.info("发送超时提醒: taskId={}, taskName={}", task.getId(), task.getTaskName());

        List<FlwTaskActor> actors = getQueryService().getTaskActorsByTaskId(task.getId());
        if (actors == null || actors.isEmpty()) {
            return;
        }

        Set<Long> userIds = actors.stream()
                .filter(actor -> actor.getActorType() != null && actor.getActorType() == 0)
                .map(actor -> {
                    try {
                        return Long.parseLong(actor.getActorId());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return;
        }

        String title = "【紧急】任务即将超时";
        String content = String.format("任务「%s」即将超时，请尽快处理！", task.getTaskName());

        for (Long userId : userIds) {
            try {
                NotificationMessage message = NotificationMessage.workflow(title, content, task.getId());
                notificationService.sendToUser(userId, message);
            } catch (Exception e) {
                log.error("发送超时提醒失败: userId={}, taskId={}", userId, task.getId(), e);
            }
        }

        log.info("超时提醒发送完成: taskId={}, recipients={}", task.getId(), userIds);
    }

    /**
     * 更新任务提醒次数
     */
    public void updateRemindCount(FlwTask task, int newRemindCount) {
        FlwTask updateTask = new FlwTask();
        updateTask.setId(task.getId());
        updateTask.setRemindRepeat(newRemindCount);

        // 设置下次提醒时间
        if (newRemindCount < 3) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.HOUR, DEFAULT_REMIND_INTERVAL_HOURS);
            updateTask.setRemindTime(calendar.getTime());
        }

        getTaskService().updateTaskById(updateTask, SYSTEM_CREATOR);
        log.debug("更新提醒次数: taskId={}, remindRepeat={}", task.getId(), newRemindCount);
    }
}