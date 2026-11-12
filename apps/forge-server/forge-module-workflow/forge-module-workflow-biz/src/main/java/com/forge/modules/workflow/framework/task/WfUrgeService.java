package com.forge.modules.workflow.framework.task;

import com.forge.modules.workflow.framework.WorkflowPermissionSupport;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.TaskService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 手动催办服务：流程催办（发起人/工作流管理员），含节流行锁与通知留痕。
 * <p>
 * 自 WfTaskServiceImpl 平移（FOR UPDATE 行锁逻辑原样搬移，一行不改语义）：
 * 节流判定与标记写入前先取行锁串行化，防并发催办双穿透与变量丢更新。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WfUrgeService {

    private final TaskService taskService;
    private final ProcessService processService;
    private final QueryService queryService;
    private final FlowLongIdentityService identityService;
    private final FlwTaskMapper taskMapper;
    private final WfApprovalCommentMapper approvalCommentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final FlowLongSchedulerProperties schedulerProperties;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void urgeInstance(String processInstanceId, String comment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }
        Long instanceId;
        try {
            instanceId = Long.parseLong(processInstanceId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "流程实例ID格式错误");
        }

        FlwInstance instance = queryService.getInstance(instanceId);
        if (instance == null) {
            throw new BusinessException(400, "流程已结束，无法催办");
        }

        boolean isInitiator = String.valueOf(currentUserId).equals(instance.getCreateId());
        if (!isInitiator && !hasWorkflowAdminRole()) {
            throw new BusinessException(403, "只有流程发起人或工作流管理员才能催办");
        }

        // 按 id 升序固定加锁顺序：并发催办同实例多任务时防互相持锁等待死锁
        List<FlwTask> approvalTasks = queryService.getTasksByInstanceId(instanceId).stream()
                .filter(t -> TaskResponseAssembler.isHumanApprovalTask(t.getTaskType()))
                .sorted(Comparator.comparing(FlwTask::getId))
                .toList();
        if (approvalTasks.isEmpty()) {
            throw new BusinessException(400, "当前无待审批任务，无需催办");
        }

        FlwProcess process = processService.getProcessById(instance.getProcessId());
        String processName = process != null ? process.getProcessName() : instance.getCurrentNodeName();
        String urgerName = identityService.getUserName(currentUserId);
        long now = System.currentTimeMillis();
        long throttleMillis = Math.max(1, schedulerProperties.getUrgeIntervalMinutes()) * 60_000L;

        int urged = 0;
        long earliestRetryMillis = Long.MAX_VALUE;
        for (FlwTask task : approvalTasks) {
            // 节流判定与标记写入须串行化：先取行锁（FOR UPDATE 当前读，持有至事务结束），
            // 再基于锁内最新 variable 判节流并合并写回——列表快照上判定会被并发催办双穿透、
            // 整体覆写 variable 会丢更新
            FlwTask lockedTask = lockTaskForUrge(task.getId());
            if (lockedTask == null) {
                // 列表加载到取锁之间任务已办结（移入历史表），无需催办
                continue;
            }
            Long lastUrgeTime = readLastUrgeTime(lockedTask);
            if (lastUrgeTime != null && now - lastUrgeTime < throttleMillis) {
                earliestRetryMillis = Math.min(earliestRetryMillis, throttleMillis - (now - lastUrgeTime));
                continue;
            }
            urgeSingleTask(lockedTask, processName, urgerName, comment, now, currentUserId);
            urged++;
        }

        if (urged == 0) {
            if (earliestRetryMillis == Long.MAX_VALUE) {
                // 没有任何任务命中节流：说明待审批任务在取锁前已全部办结
                throw new BusinessException(400, "当前无待审批任务，无需催办");
            }
            long retryMinutes = Math.max(1, (earliestRetryMillis + 60_000 - 1) / 60_000);
            throw new BusinessException(400, "催办过于频繁，请约 " + retryMinutes + " 分钟后再试");
        }
        log.info("流程催办完成: instanceId={}, operator={}, urged={}/{}, comment={}",
                instanceId, currentUserId, urged, approvalTasks.size(), comment);
    }

    /**
     * 催办前锁定任务行：SELECT ... FOR UPDATE（H2/MySQL 均支持），返回锁内最新任务行；
     * 任务已不存在（并发办结）返回 null。须在事务内调用，行锁随事务提交/回滚释放。
     */
    private FlwTask lockTaskForUrge(Long taskId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<FlwTask>()
                .eq(FlwTask::getId, taskId)
                .last("FOR UPDATE"));
    }

    /**
     * 催办单个任务：写节流标记 + 通知处理人 + 审批意见留痕
     * <p>
     * task 须为 {@link #lockTaskForUrge} 取得的锁内最新行：节流标记在其 variable 上合并后写回，
     * 行锁保证读-改-写期间无并发催办覆写。
     */
    private void urgeSingleTask(FlwTask task, String processName, String urgerName,
                                String comment, long now, Long operatorUserId) {
        // 节流标记写任务变量（不影响流程变量，仅任务列 variable）
        FlwTask update = new FlwTask();
        update.setId(task.getId());
        Map<String, Object> variableMap = task.variableMap();
        if (variableMap == null) {
            variableMap = new HashMap<>();
        }
        variableMap.put("lastUrgeTime", now);
        try {
            update.setVariable(objectMapper.writeValueAsString(variableMap));
        } catch (Exception e) {
            log.warn("序列化催办标记失败，跳过节流写入: taskId={}", task.getId());
        }
        taskService.updateTaskById(update, FlowCreatorFactory.system("催办"));

        // 通知该任务全部用户参与者
        long waitingMinutes = task.getCreateTime() == null ? 0
                : Math.max(1, (now - task.getCreateTime().getTime()) / 60_000);
        String title = "【催办】待办任务处理提醒";
        String content = StrUtil.format("{} 催办流程「{}」，任务「{}」已等待约 {} 分钟，请及时处理。",
                urgerName, processName, task.getTaskName(), waitingMinutes);
        if (StrUtil.isNotBlank(comment)) {
            content = content + " 催办留言：" + comment;
        }
        List<FlwTaskActor> actors = queryService.getTaskActorsByTaskId(task.getId());
        Set<Long> actorUserIds = actors.stream()
                .filter(a -> ActorType.user.eq(a.getActorType()))
                .map(a -> {
                    try {
                        return Long.parseLong(a.getActorId());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 通知事件化（评审 §4.4-W1-P1#3）：事务内发布、AFTER_COMMIT 提交后由
        // TaskNotifyEventHandler 发送——回滚不产生幽灵通知，慢发送不占用事务线程
        if (!actorUserIds.isEmpty()) {
            eventPublisher.publishEvent(new com.forge.modules.workflow.listener.event.TaskNotificationEvent(
                    task.getId(), task.getInstanceId(), List.copyOf(actorUserIds), title, content));
        }

        // 审批意见留痕（挂在催办的任务上）
        saveUrgeApprovalComment(task, operatorUserId, urgerName,
                StrUtil.isNotBlank(comment) ? "催办：" + comment : "催办任务「" + task.getTaskName() + "」");
    }

    /**
     * 催办留痕：构建审批意见并落库（无附件路径，字段构建与主类 saveApprovalComment 同构；
     * 待 WfApprovalCommentService（任务 #3）合并落地后收敛为统一引用）
     */
    private void saveUrgeApprovalComment(FlwTask task, Long userId, String userName, String commentText) {
        WfApprovalComment comment = new WfApprovalComment();
        comment.setProcessInstanceId(task.getInstanceId());
        comment.setTaskId(task.getId());
        comment.setTaskDefKey(task.getTaskKey());
        comment.setTaskName(task.getTaskName());
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setActionType(ApprovalActionTypeEnum.URGE.getCode());
        comment.setCommentText(commentText);
        comment.setCreateTime(LocalDateTime.now());
        approvalCommentMapper.insert(comment);
    }

    private Long readLastUrgeTime(FlwTask task) {
        Map<String, Object> variableMap = task.variableMap();
        if (variableMap == null) {
            return null;
        }
        Object value = variableMap.get("lastUrgeTime");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && str.matches("\\d+")) {
            return Long.parseLong(str);
        }
        return null;
    }

    /**
     * 工作流管理员判定（与 WfTaskServiceImpl#hasWorkflowAdminRole 同构；公共类抽取后统一收敛引用）
     */
    private boolean hasWorkflowAdminRole() {
        return WorkflowPermissionSupport.hasWorkflowAdminRole();
    }
}
