package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.*;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.FlowLongEngineImpl;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.AgentType;
import com.aizuda.bpm.engine.core.enums.PerformType;
import com.aizuda.bpm.engine.core.enums.TaskState;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.engine.model.ModelHelper;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.common.utils.UserContext;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.config.FlowLongSchedulerProperties;
import com.forge.modules.workflow.dto.task.*;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.framework.title.InstanceTitleRenderer;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.service.WfTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流任务管理服务实现 - FlowLong 版本
 *
 * @author forge-admin
 */
@Slf4j
@Service("wfTaskService")
@RequiredArgsConstructor
public class WfTaskServiceImpl implements WfTaskService {

    private final TaskService taskService;
    private final ProcessService processService;
    private final RuntimeService runtimeService;
    private final QueryService queryService;
    private final FlowLongIdentityService identityService;
    private final WfApprovalCommentMapper approvalCommentMapper;
    private final FlwTaskMapper taskMapper;
    private final FlwHisTaskMapper hisTaskMapper;
    private final FlwTaskActorMapper taskActorMapper;
    private final FlwHisTaskActorMapper hisTaskActorMapper;
    private final FlwInstanceMapper instanceMapper;
    private final FlwHisInstanceMapper hisInstanceMapper;
    private final FlowLongEngine flowLongEngine;
    private final SysAttachmentService sysAttachmentService;
    private final NotificationService notificationService;
    private final FlowLongSchedulerProperties schedulerProperties;
    private final ObjectMapper objectMapper;

    private static final String COMMENT_ATTACHMENT_BIZ_TYPE = "wf_comment";

    @Override
    public Page<TaskResponse> getTodoTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userIdStr = String.valueOf(currentUserId);

        // 查询当前用户作为参与者的待办任务
        // 1. 先查询 flw_task_actor 表，找到当前用户参与的 taskId
        LambdaQueryWrapper<FlwTaskActor> actorWrapper = new LambdaQueryWrapper<>();
        actorWrapper.eq(FlwTaskActor::getActorId, userIdStr);
        List<FlwTaskActor> taskActors = taskActorMapper.selectList(actorWrapper);

        if (taskActors.isEmpty()) {
            Page<TaskResponse> emptyPage = new Page<>();
            emptyPage.setCurrent(request.getPageNum());
            emptyPage.setSize(request.getPageSize());
            emptyPage.setTotal(0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        // 2. 获取所有 taskId
        Set<Long> taskIds = taskActors.stream()
                .map(FlwTaskActor::getTaskId)
                .collect(Collectors.toSet());

        // 3. 查询 flw_task 表，筛选活动任务
        LambdaQueryWrapper<FlwTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(FlwTask::getId, taskIds);

        // 任务名称筛选
        if (StrUtil.isNotBlank(request.getName())) {
            taskWrapper.like(FlwTask::getTaskName, request.getName());
        }

        // 标题过滤两步式：先按 __title 圈实例，再 in 任务（顶层谓词避 EXISTS 方言差异）
        String titleKeyword = escapeLike(request.getTitle());
        if (titleKeyword != null) {
            List<Long> titleInstanceIds = instanceMapper.selectList(new LambdaQueryWrapper<FlwInstance>()
                            .select(FlwInstance::getId)
                            .apply("variable LIKE CONCAT('%\"__title\":\"%', {0}, '%') ESCAPE '|'", titleKeyword))
                    .stream().map(FlwInstance::getId).toList();
            if (titleInstanceIds.isEmpty()) {
                return emptyTaskPage(request);
            }
            taskWrapper.in(FlwTask::getInstanceId, titleInstanceIds);
        }

        taskWrapper.orderByDesc(FlwTask::getCreateTime);

        Page<FlwTask> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwTask> taskPage = taskMapper.selectPage(pageParam, taskWrapper);

        // 本人从候选组（角色/部门）签收的任务标记，供前端展示“取消签收”
        Set<Long> claimedFromGroupTaskIds = taskActors.stream()
                .filter(a -> AgentType.claimRole.eq(a.getAgentType()) || AgentType.claimDepartment.eq(a.getAgentType()))
                .map(FlwTaskActor::getTaskId)
                .collect(Collectors.toSet());

        // 4. 批量预取实例/流程/发起人（去 N+1：每页 3 次批量替代逐行 3N 次）
        BatchContext ctx = preloadBatch(taskPage.getRecords(), false);
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(task -> {
                    TaskResponse response = convertTaskToResponseWithProcess(task, ctx);
                    response.setClaimedFromGroup(claimedFromGroupTaskIds.contains(task.getId()));
                    return response;
                })
                .collect(Collectors.toList());

        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public Page<TaskResponse> getClaimableTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        // 用户经角色/部门候选池可见的任务：actor 记录的角色/部门 ID ∈ 用户隶属集
        // 角色直接取 JWT 过滤器装载的 UserContext（免 DB 查询）；部门另查用户主表
        Set<String> groupIds = new HashSet<>();
        UserContext context = UserContext.get();
        if (context != null && context.getRoles() != null) {
            context.getRoles().forEach(role -> {
                if (role.getRoleId() != null) {
                    groupIds.add(String.valueOf(role.getRoleId()));
                }
            });
        }
        Long deptId = identityService.getUserDeptId(currentUserId);
        if (deptId != null) {
            groupIds.add(String.valueOf(deptId));
        }
        if (groupIds.isEmpty()) {
            return emptyPage(request);
        }

        LambdaQueryWrapper<FlwTaskActor> actorWrapper = new LambdaQueryWrapper<>();
        actorWrapper.in(FlwTaskActor::getActorId, groupIds);
        actorWrapper.in(FlwTaskActor::getActorType, List.of(ActorType.role.getValue(), ActorType.department.getValue()));
        List<FlwTaskActor> groupActors = taskActorMapper.selectList(actorWrapper);
        if (groupActors.isEmpty()) {
            return emptyPage(request);
        }

        Map<Long, List<FlwTaskActor>> actorsByTask = groupActors.stream()
                .collect(Collectors.groupingBy(FlwTaskActor::getTaskId));

        LambdaQueryWrapper<FlwTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(FlwTask::getId, actorsByTask.keySet());
        if (StrUtil.isNotBlank(request.getName())) {
            taskWrapper.like(FlwTask::getTaskName, request.getName());
        }
        taskWrapper.orderByDesc(FlwTask::getCreateTime);

        Page<FlwTask> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwTask> taskPage = taskMapper.selectPage(pageParam, taskWrapper);

        BatchContext claimCtx = preloadBatch(taskPage.getRecords(), false);
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(task -> {
                    TaskResponse response = convertTaskToResponseWithProcess(task, claimCtx);
                    response.setCandidate(true);
                    response.setCandidateUsers(actorsByTask.getOrDefault(task.getId(), Collections.emptyList()).stream()
                            .map(FlwTaskActor::getActorName)
                            .distinct()
                            .collect(Collectors.toList()));
                    return response;
                })
                .collect(Collectors.toList());

        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 批量预取上下文：一次查询实例/流程/发起人名，替代转换链逐行查询
     */
    private static class BatchContext {
        final Map<Long, FlwInstance> instanceMap = new HashMap<>();
        final Map<Long, FlwHisInstance> hisInstanceMap = new HashMap<>();
        final Map<Long, FlwProcess> processMap = new HashMap<>();
        final Map<Long, String> userNameMap = new HashMap<>();
        // his 转换专用（已办/抄送）
        final Map<Long, WfApprovalComment> latestCommentByTask = new HashMap<>();
        final Map<Long, List<FlwHisTask>> hisTasksByInstance = new HashMap<>();
        final Map<Long, List<FlwTask>> activeByParentTask = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private BatchContext preloadBatch(List<? extends FlwTask> tasks, boolean his) {
        BatchContext ctx = new BatchContext();
        if (tasks == null || tasks.isEmpty()) return ctx;

        Set<Long> instanceIds = tasks.stream().map(FlwTask::getInstanceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> processIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();

        if (!instanceIds.isEmpty()) {
            if (his) {
                List<FlwHisInstance> instances = hisInstanceMapper.selectBatchIds(instanceIds);
                instances.forEach(i -> {
                    ctx.hisInstanceMap.put(i.getId(), i);
                    if (i.getProcessId() != null) processIds.add(i.getProcessId());
                    if (i.getCreateId() != null) {
                        try { userIds.add(Long.parseLong(i.getCreateId())); } catch (NumberFormatException ignored) {}
                    }
                });
            } else {
                List<FlwInstance> instances = instanceMapper.selectBatchIds(instanceIds);
                instances.forEach(i -> {
                    ctx.instanceMap.put(i.getId(), i);
                    if (i.getProcessId() != null) processIds.add(i.getProcessId());
                    if (i.getCreateId() != null) {
                        try { userIds.add(Long.parseLong(i.getCreateId())); } catch (NumberFormatException ignored) {}
                    }
                });
            }
        }
        if (!processIds.isEmpty()) {
            processIds.forEach(pid -> {
                FlwProcess process = processService.getProcessById(pid);
                if (process != null) ctx.processMap.put(pid, process);
            });
        }
        if (!userIds.isEmpty()) {
            ctx.userNameMap.putAll(identityService.getUserNames(userIds));
        }
        return ctx;
    }

    /**
     * 已办/抄送列表批量预取：实例/流程/发起人 + 意见(按任务取最新) + 实例任务序(算下一节点) + 拿回后继
     */
    private BatchContext preloadHisBatch(List<FlwHisTask> hisTasks) {
        BatchContext ctx = new BatchContext();
        if (hisTasks == null || hisTasks.isEmpty()) return ctx;

        // 1. 批量实例/流程/发起人（与活动侧同一套填充逻辑）
        fillInstanceProcessUser(ctx, hisTasks.stream().map(FlwHisTask::getInstanceId).collect(Collectors.toSet()));

        // 2. 实例内全部历史任务（升序）→ 下一节点名与意见任务分组共用
        Set<Long> instanceIds = ctx.hisInstanceMap.keySet();
        if (!instanceIds.isEmpty()) {
            LambdaQueryWrapper<FlwHisTask> hisWrapper = new LambdaQueryWrapper<>();
            hisWrapper.in(FlwHisTask::getInstanceId, instanceIds).orderByAsc(FlwHisTask::getCreateTime);
            hisTaskMapper.selectList(hisWrapper).forEach(t ->
                    ctx.hisTasksByInstance.computeIfAbsent(t.getInstanceId(), k -> new ArrayList<>()).add(t));
        }

        // 3. 意见：按任务 ID 分组，升序遍历后写覆盖 → 保留每任务最新一条
        if (!instanceIds.isEmpty()) {
            LambdaQueryWrapper<WfApprovalComment> commentWrapper = new LambdaQueryWrapper<>();
            commentWrapper.in(WfApprovalComment::getProcessInstanceId, instanceIds)
                    .orderByAsc(WfApprovalComment::getCreateTime);
            for (WfApprovalComment comment : approvalCommentMapper.selectList(commentWrapper)) {
                if (comment.getTaskId() != null) {
                    ctx.latestCommentByTask.put(comment.getTaskId(), comment);
                }
            }
        }

        // 4. 拿回后继：一次查全部 parentTaskId 命中的活动任务 + 运行中实例集合
        Set<Long> hisTaskIds = hisTasks.stream().map(FlwHisTask::getId).collect(Collectors.toSet());
        if (!hisTaskIds.isEmpty()) {
            LambdaQueryWrapper<FlwTask> activeWrapper = new LambdaQueryWrapper<>();
            activeWrapper.in(FlwTask::getParentTaskId, hisTaskIds);
            taskMapper.selectList(activeWrapper).forEach(t ->
                    ctx.activeByParentTask.computeIfAbsent(t.getParentTaskId(), k -> new ArrayList<>()).add(t));
            Set<Long> runningIds = hisTasks.stream().map(FlwHisTask::getInstanceId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!runningIds.isEmpty()) {
                LambdaQueryWrapper<FlwInstance> runningWrapper = new LambdaQueryWrapper<>();
                runningWrapper.in(FlwInstance::getId, runningIds);
                instanceMapper.selectList(runningWrapper).forEach(i -> ctx.instanceMap.put(i.getId(), i));
            }
        }
        return ctx;
    }

    /** 实例/流程/发起人批量填充（活动与历史侧共用） */
    private void fillInstanceProcessUser(BatchContext ctx, Set<Long> instanceIds) {
        if (instanceIds.isEmpty()) return;
        Set<Long> processIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        List<FlwHisInstance> hisInstances = hisInstanceMapper.selectBatchIds(instanceIds);
        hisInstances.forEach(i -> {
            ctx.hisInstanceMap.put(i.getId(), i);
            if (i.getProcessId() != null) processIds.add(i.getProcessId());
            if (StrUtil.isNotBlank(i.getCreateId())) {
                try { userIds.add(Long.parseLong(i.getCreateId())); } catch (NumberFormatException ignored) {}
            }
        });
        processIds.forEach(pid -> {
            FlwProcess process = processService.getProcessById(pid);
            if (process != null) ctx.processMap.put(pid, process);
        });
        if (!userIds.isEmpty()) {
            ctx.userNameMap.putAll(identityService.getUserNames(userIds));
        }
    }

    /**
     * 已办/抄送转换（批量上下文版：零逐行查询）
     */
    private TaskResponse convertHisTaskToResponseWithProcess(FlwHisTask hisTask, BatchContext ctx) {
        TaskResponse response = convertHisTaskToResponse(hisTask);

        FlwHisInstance hisInstance = ctx.hisInstanceMap.get(hisTask.getInstanceId());
        if (hisInstance != null) {
            response.setProcessInstanceId(String.valueOf(hisInstance.getId()));
            response.setUrgent(hisInstance.getUrgent());
            response.setInstanceTitle(InstanceTitleRenderer.extractTitle(hisInstance.getVariable()));
            FlwProcess process = ctx.processMap.get(hisInstance.getProcessId());
            if (process != null) {
                response.setProcessDefinitionId(String.valueOf(process.getId()));
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }
            if (StrUtil.isNotBlank(hisInstance.getCreateId())) {
                response.setStartUserId(hisInstance.getCreateId());
                try {
                    Long startUserId = Long.parseLong(hisInstance.getCreateId());
                    response.setStartUserName(ctx.userNameMap.getOrDefault(startUserId, hisInstance.getCreateBy()));
                } catch (NumberFormatException e) {
                    response.setStartUserName(hisInstance.getCreateBy());
                }
            }
            response.setStartTime(hisInstance.getCreateTime());
            response.setProcessNo(hisInstance.getInstanceNo());
        }

        // 审批意见（预取 Map，每任务最新一条）
        WfApprovalComment approvalComment = ctx.latestCommentByTask.get(hisTask.getId());
        if (approvalComment != null) {
            response.setActionType(approvalComment.getActionType());
            response.setCommentText(approvalComment.getCommentText());
            if (approvalComment.getCreateTime() != null) {
                response.setEndTime(Date.from(approvalComment.getCreateTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }
        }

        response.setWithdrawn(
                hisTask.getTaskType().equals(TaskType.withdraw.getValue())
                || hisTask.getTaskType().equals(TaskType.saveAsDraft.getValue())
                || ApprovalActionTypeEnum.WITHDRAW.getCode().equals(response.getActionType())
        );

        // 下一节点名：实例任务升序列表中找 createTime 晚于本任务 finishTime 的第一条
        if (hisTask.getFinishTime() != null) {
            List<FlwHisTask> sequence = ctx.hisTasksByInstance.getOrDefault(hisTask.getInstanceId(), Collections.emptyList());
            for (FlwHisTask candidate : sequence) {
                if (candidate.getCreateTime() != null && candidate.getCreateTime().after(hisTask.getFinishTime())) {
                    response.setNextActivityName(candidate.getTaskName());
                    break;
                }
            }
        }

        // 拿回标记：实例运行中且后继待人审批任务存在
        if (isHumanApprovalTask(hisTask.getTaskType()) && ctx.instanceMap.containsKey(hisTask.getInstanceId())) {
            List<FlwTask> successors = ctx.activeByParentTask.getOrDefault(hisTask.getId(), Collections.emptyList());
            response.setCanReclaim(successors.stream().anyMatch(this::isHumanApprovalTaskByType));
        }

        return response;
    }

    private boolean isHumanApprovalTaskByType(FlwTask task) {
        return isHumanApprovalTask(task.getTaskType());
    }

    /**
     * 转换任务响应（使用批量预取上下文，无逐行查询）
     */
    private TaskResponse convertTaskToResponseWithProcess(FlwTask task, BatchContext ctx) {
        TaskResponse response = convertTaskToResponse(task);
        FlwInstance instance = ctx.instanceMap.get(task.getInstanceId());
        if (instance != null) {
            response.setProcessInstanceId(String.valueOf(instance.getId()));
            response.setUrgent(instance.getUrgent());
            response.setInstanceTitle(InstanceTitleRenderer.extractTitle(instance.getVariable()));
            FlwProcess process = ctx.processMap.get(instance.getProcessId());
            if (process != null) {
                response.setProcessDefinitionId(String.valueOf(process.getId()));
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }
            if (StrUtil.isNotBlank(instance.getCreateId())) {
                response.setStartUserId(instance.getCreateId());
                try {
                    Long startUserId = Long.parseLong(instance.getCreateId());
                    response.setStartUserName(ctx.userNameMap.getOrDefault(startUserId, instance.getCreateBy()));
                } catch (NumberFormatException e) {
                    response.setStartUserName(instance.getCreateBy());
                }
            }
            response.setStartTime(instance.getCreateTime());
            response.setProcessNo(instance.getInstanceNo());
        }
        return response;
    }

    private Page<TaskResponse> emptyPage(TaskQueryRequest request) {
        Page<TaskResponse> emptyPage = new Page<>();
        emptyPage.setCurrent(request.getPageNum());
        emptyPage.setSize(request.getPageSize());
        emptyPage.setTotal(0);
        emptyPage.setRecords(Collections.emptyList());
        return emptyPage;
    }

    @Override
    public Page<TaskResponse> getDoneTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userIdStr = String.valueOf(currentUserId);

        // 查询当前用户作为参与者的历史任务
        // 1. 先查询 flw_his_task_actor 表，找到当前用户参与的历史 taskId
        LambdaQueryWrapper<FlwHisTaskActor> actorWrapper = new LambdaQueryWrapper<>();
        actorWrapper.eq(FlwHisTaskActor::getActorId, userIdStr);
        List<FlwHisTaskActor> hisTaskActors = hisTaskActorMapper.selectList(actorWrapper);

        if (hisTaskActors.isEmpty()) {
            Page<TaskResponse> emptyPage = new Page<>();
            emptyPage.setCurrent(request.getPageNum());
            emptyPage.setSize(request.getPageSize());
            emptyPage.setTotal(0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        // 2. 获取所有历史 taskId
        Set<Long> hisTaskIds = hisTaskActors.stream()
                .map(FlwHisTaskActor::getTaskId)
                .collect(Collectors.toSet());

        // 3. 查询 flw_his_task 表，筛选已完成的任务
        LambdaQueryWrapper<FlwHisTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(FlwHisTask::getId, hisTaskIds);
        // 只查询已完成的任务（task_state > 0 表示已完成）
        taskWrapper.gt(FlwHisTask::getTaskState, 0);
        // 排除发起节点的发起人任务（taskType=0），已办只展示真实审批记录
        taskWrapper.ne(FlwHisTask::getTaskType, 0);

        // 任务名称筛选
        if (StrUtil.isNotBlank(request.getName())) {
            taskWrapper.like(FlwHisTask::getTaskName, request.getName());
        }

        // 标题过滤两步式：先按 __title 圈实例。流程可能仍在运行（实例在活动表）或已结束（在历史表），
        // 已办任务两态并存，故两侧并集
        String titleKeyword = escapeLike(request.getTitle());
        if (titleKeyword != null) {
            Set<Long> titleInstanceIds = new java.util.HashSet<>(instanceMapper.selectList(
                            new LambdaQueryWrapper<FlwInstance>()
                                    .select(FlwInstance::getId)
                                    .apply("variable LIKE CONCAT('%\"__title\":\"%', {0}, '%') ESCAPE '|'", titleKeyword))
                    .stream().map(FlwInstance::getId).toList());
            titleInstanceIds.addAll(hisInstanceMapper.selectList(
                            new LambdaQueryWrapper<FlwHisInstance>()
                                    .select(FlwHisInstance::getId)
                                    .apply("variable LIKE CONCAT('%\"__title\":\"%', {0}, '%') ESCAPE '|'", titleKeyword))
                    .stream().map(FlwHisInstance::getId).toList());
            if (titleInstanceIds.isEmpty()) {
                return emptyTaskPage(request);
            }
            taskWrapper.in(FlwHisTask::getInstanceId, titleInstanceIds);
        }

        taskWrapper.orderByDesc(FlwHisTask::getFinishTime);

        Page<FlwHisTask> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwHisTask> taskPage = hisTaskMapper.selectPage(pageParam, taskWrapper);

        // 4. 转换为响应对象，补充流程信息
        BatchContext hisCtx = preloadHisBatch(taskPage.getRecords());
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(t -> convertHisTaskToResponseWithProcess(t, hisCtx))
                .collect(Collectors.toList());

        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public TaskResponse getTaskById(String taskId) {
        Long id = parseTaskId(taskId);
        FlwTask task = queryService.getTask(id);
        if (task == null) {
            FlwHisTask hisTask = queryService.getHistTask(id);
            if (hisTask == null) {
                throw new BusinessException(404, "任务不存在");
            }
            return convertHisTaskToResponse(hisTask);
        }
        return convertTaskToResponse(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimTask(String taskId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        Long id = parseTaskId(taskId);
        FlwTask task = queryService.getTask(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在或已完成");
        }

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        taskService.claimRole(id, flowCreator);

        log.info("任务签收成功：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unclaimTask(String taskId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }
        Long id = parseTaskId(taskId);
        FlwTask task = queryService.getTask(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在或已完成");
        }

        // 定位本人从候选组签收的痕迹（签收后 agentType=认领角色/认领部门，agentId=原组ID）
        FlwTaskActor claimed = queryService.getTaskActorsByTaskId(id).stream()
                .filter(a -> String.valueOf(currentUserId).equals(a.getActorId()))
                .filter(a -> AgentType.claimRole.eq(a.getAgentType()) || AgentType.claimDepartment.eq(a.getAgentType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "该任务不是您从候选组签收的，无法取消签收"));

        taskService.changeTaskActor(id, restoreGroupActor(task, claimed));
        log.info("取消签收成功：taskId={}, userId={}, restoredGroup={}", taskId, currentUserId, claimed.getAgentId());
    }

    /**
     * 由认领痕迹（ext 快照 + agentId）还原原候选组参与者
     */
    private FlwTaskActor restoreGroupActor(FlwTask task, FlwTaskActor claimed) {
        FlwTaskActor restored = new FlwTaskActor();
        restored.setTaskId(task.getId());
        restored.setInstanceId(task.getInstanceId());
        restored.setTenantId(FlowCreatorFactory.currentTenantId());
        restored.setActorId(claimed.getAgentId());
        // ext 缺失时按认领方式推导组类型：认领角色→角色，认领部门→部门
        restored.setActorType(AgentType.claimDepartment.eq(claimed.getAgentType())
                ? ActorType.department.getValue() : ActorType.role.getValue());
        restored.setActorName(claimed.getActorName());
        if (StrUtil.isNotBlank(claimed.getExt())) {
            try {
                Map<String, Object> ext = objectMapper.readValue(claimed.getExt(), Map.class);
                if (ext.get("actorType") instanceof Number actorType) {
                    restored.setActorType(actorType.intValue());
                }
                if (ext.get("actorName") instanceof String actorName) {
                    restored.setActorName(actorName);
                }
            } catch (Exception e) {
                log.warn("解析认领痕迹ext失败，使用认领记录字段还原: taskId={}", task.getId());
            }
        }
        return restored;
    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", true);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        FlowCreator flowCreator = createFlowCreator(currentUserId);
//        taskService.complete(id, flowCreator, variables);

        flowLongEngine.executeTask(id,flowCreator,variables);
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.APPROVE.getCode(), request.getComment(), request.getAttachmentIds());
        log.info("任务审批通过：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.REJECT.getCode(), request.getComment(), request.getAttachmentIds());

        // 驳回任务：节点未配置驳回策略时维持既有行为（直接终止）；
        // 配置后交引擎按策略分派（1 回发起人 / 2 上一节点 / 3 指定节点，目标取 extendConfig.rejectTargetKey）
        FlowCreator flowCreator = createFlowCreator(currentUserId);
        Integer rejectStrategy = readRejectStrategy(task);
        if (rejectStrategy == null) {
            flowLongEngine.executeRejectTask(task, null, flowCreator, request.getVariables(), true);
        } else if (rejectStrategy == 3) {
            String targetKey = readRejectTargetKey(task);
            if (targetKey == null || targetKey.isEmpty()) {
                throw new BusinessException(400, "节点驳回策略为指定节点但未配置目标，请联系流程管理员");
            }
            flowLongEngine.executeRejectTask(task, targetKey, flowCreator, request.getVariables(), false);
        } else {
            flowLongEngine.executeRejectTask(task, null, flowCreator, request.getVariables(), false);
        }
        log.info("任务审批驳回：taskId={}, userId={}, rejectStrategy={}", taskId, currentUserId, rejectStrategy);
    }

    /**
     * 读取任务所在节点的驳回策略（实例级模型快照）
     */
    private Integer readRejectStrategy(FlwTask task) {
        try {
            com.aizuda.bpm.engine.model.NodeModel nodeModel = taskService.getTaskModel(task.getId());
            return nodeModel == null ? null : nodeModel.getRejectStrategy();
        } catch (Exception e) {
            log.warn("读取节点驳回策略失败，按默认终止处理: taskId={}", task.getId(), e);
            return null;
        }
    }

    /**
     * 读取策略 3 的目标节点 key（封装层语义，存节点 extendConfig.rejectTargetKey）
     */
    private String readRejectTargetKey(FlwTask task) {
        try {
            com.aizuda.bpm.engine.model.NodeModel nodeModel = taskService.getTaskModel(task.getId());
            if (nodeModel == null || nodeModel.getExtendConfig() == null) {
                return null;
            }
            Object target = nodeModel.getExtendConfig().get("rejectTargetKey");
            return target == null ? null : target.toString();
        } catch (Exception e) {
            log.warn("读取驳回目标节点失败: taskId={}", task.getId(), e);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(String taskId, TaskDelegateRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        FlowCreator delegateCreator = createFlowCreator(request.getDelegateUserId());
        taskService.delegateTask(id, flowCreator, delegateCreator);

        String delegateUserName = identityService.getUserName(request.getDelegateUserId());
        String commentText = StrUtil.isNotBlank(request.getComment())
                ? request.getComment()
                : "任务委派给：" + delegateUserName;
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.DELEGATE.getCode(), commentText);

        log.info("任务委派：taskId={}, fromUser={}, toUser={}", taskId, currentUserId, request.getDelegateUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(String taskId, TaskTransferRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        FlowCreator transferCreator = createFlowCreator(request.getTransferUserId());
        taskService.transferTask(id, flowCreator, transferCreator);

        String transferUserName = identityService.getUserName(request.getTransferUserId());
        String commentText = StrUtil.isNotBlank(request.getComment())
                ? request.getComment()
                : "任务转办给：" + transferUserName;
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.TRANSFER.getCode(), commentText);

        log.info("任务转办：taskId={}, fromUser={}, toUser={}", taskId, currentUserId, request.getTransferUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(String taskId, TaskReturnRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);

        // 使用 FlowLongEngine 执行退回，内部会正确构建 Execution 对象
        flowLongEngine.executeJumpTask(id, request.getTargetTaskDefKey(), flowCreator, null, TaskType.rejectJump);

        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.RETURN.getCode(), request.getComment());
        log.info("任务退回：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    public List<Map<String, String>> getReturnNodes(String taskId) {
        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        // 获取流程实例的所有历史任务
        Optional<List<FlwHisTask>> hisTasksOpt = queryService.getHisTasksByInstanceId(task.getInstanceId());
        if (hisTasksOpt.isEmpty() || hisTasksOpt.get().isEmpty()) {
            return Collections.emptyList();
        }

        List<FlwHisTask> hisTasks = hisTasksOpt.get();

        // 筛选可退回的节点
        // 1. 已完成的任务（taskState > 0）
        // 2. 排除抄送、触发器、定时器等特殊任务类型
        // 3. 排除当前任务节点
        // 4. 只保留审批节点和发起人节点
        List<Map<String, String>> returnNodes = new ArrayList<>();
        Set<String> addedNodeKeys = new HashSet<>(); // 避免重复节点

        for (FlwHisTask hisTask : hisTasks) {
            // 排除未完成的任务
            if (hisTask.getTaskState() == null || hisTask.getTaskState() <= 0) {
                continue;
            }

            // 排除特殊任务类型：抄送、触发器、定时器
            Integer taskType = hisTask.getTaskType();
            if (TaskType.cc.eq(taskType) || TaskType.trigger.eq(taskType) || TaskType.timer.eq(taskType)) {
                continue;
            }

            // 排除当前任务节点
            if (Objects.equals(hisTask.getTaskKey(), task.getTaskKey())) {
                continue;
            }

            // 只保留审批节点和发起人节点
            // TaskType.major = 0 (发起人), TaskType.approval = 1 (审批人)
            if (!TaskType.major.eq(taskType) && !TaskType.approval.eq(taskType)) {
                continue;
            }

            // 避免重复添加同一节点
            if (addedNodeKeys.contains(hisTask.getTaskKey())) {
                continue;
            }
            addedNodeKeys.add(hisTask.getTaskKey());

            Map<String, String> node = new HashMap<>();
            node.put("taskDefKey", hisTask.getTaskKey());
            node.put("taskName", hisTask.getTaskName());
            returnNodes.add(node);
        }

        // 按完成时间倒序排列（最近的节点在前）
        returnNodes.sort((a, b) -> {
            // 发起人节点始终放在最后
            if ("major".equals(a.get("taskDefKey")) || a.get("taskDefKey").startsWith("start")) {
                return 1;
            }
            if ("major".equals(b.get("taskDefKey")) || b.get("taskDefKey").startsWith("start")) {
                return -1;
            }
            return 0;
        });

        return returnNodes;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signCreateTask(String taskId, TaskSignCreateRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        String userNames = request.getUserIds().stream()
                .map(userIdStr -> identityService.getUserName(Long.parseLong(userIdStr)))
                .collect(Collectors.joining(", "));

        String typeLabel;
        switch (request.getType()) {
            case "before" -> {
                typeLabel = "前加签";
                flowLongEngine.executeAppendNodeModel(id, buildAppendNode(request, currentUserId),
                        flowCreator, null, true);
            }
            case "after" -> {
                typeLabel = "后加签";
                flowLongEngine.executeAppendNodeModel(id, buildAppendNode(request, currentUserId),
                        flowCreator, null, false);
            }
            case "parallel" -> {
                typeLabel = "并行加签";
                // 引擎对已存在的 actorId 静默跳过：全量重复时明确报错，避免"记录有了人没多"
                List<FlwTask> nodeTasks = queryService.getTasksByInstanceIdAndTaskKey(task.getInstanceId(), task.getTaskKey());
                Set<String> existingActorIds = nodeTasks.stream()
                        .flatMap(t -> queryService.getTaskActorsByTaskId(t.getId()).stream())
                        .map(FlwTaskActor::getActorId)
                        .collect(Collectors.toSet());
                List<String> newUserIds = request.getUserIds().stream()
                        .filter(userId -> !existingActorIds.contains(userId))
                        .toList();
                if (newUserIds.isEmpty()) {
                    throw new BusinessException(400, "所选用户已是当前任务参与人，无需重复加签");
                }
                List<FlwTaskActor> taskActors = newUserIds.stream()
                        .map(userIdStr -> createTaskActor(Long.parseLong(userIdStr)))
                        .toList();
                // 传任务原参与方式：或签任务加人仍为或签，不强转会签
                PerformType originalPerformType = task.getPerformType() == null
                        ? PerformType.countersign : PerformType.get(task.getPerformType());
                taskService.addTaskActor(id, originalPerformType, taskActors, flowCreator);
            }
            default -> throw new BusinessException(400, "不支持的加签类型：" + request.getType());
        }

        String commentText = typeLabel + "，人员：" + userNames
                + (StrUtil.isNotBlank(request.getReason()) ? "，原因：" + request.getReason() : "");
        saveApprovalComment(task, currentUserId, identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.SIGN_CREATE.getCode(), commentText);
        log.info("任务加签成功：taskId={}, type={}, userIds={}", taskId, request.getType(), request.getUserIds());
    }

    /**
     * 构造加签临时节点（NodeState.temp 由引擎置位，仅写入实例模型快照）
     */
    private NodeModel buildAppendNode(TaskSignCreateRequest request, Long operatorUserId) {
        int examineMode = request.getExamineMode() == null || request.getExamineMode() < 1 || request.getExamineMode() > 3
                ? 2 : request.getExamineMode();
        NodeModel nodeModel = new NodeModel();
        nodeModel.setNodeKey(ModelHelper.generateNodeKey());
        nodeModel.setNodeName("加签-" + identityService.getUserName(operatorUserId));
        nodeModel.setType(TaskType.approval.getValue());
        nodeModel.setExamineMode(examineMode);
        nodeModel.setNodeAssigneeList(request.getUserIds().stream()
                .map(userIdStr -> {
                    NodeAssignee assignee = new NodeAssignee();
                    assignee.setId(userIdStr);
                    assignee.setName(identityService.getUserName(Long.parseLong(userIdStr)));
                    return assignee;
                })
                .toList());
        return nodeModel;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signDeleteTask(String taskId, TaskSignDeleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        taskService.removeTaskActor(id, Collections.singletonList(request.getActorId()), flowCreator);

        String actorName = identityService.getUserName(Long.parseLong(request.getActorId()));
        String commentText = "减签人员：" + actorName
                + (StrUtil.isNotBlank(request.getReason()) ? "，原因：" + request.getReason() : "");
        saveApprovalComment(task, currentUserId, identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.SIGN_DELETE.getCode(), commentText);
        log.info("任务减签成功：taskId={}, actorId={}", taskId, request.getActorId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyTask(String taskId, TaskCopyRequest request) {
        // FlowLong 使用 createCcTask 实现抄送
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);

        // 创建抄送任务参与者列表
        List<FlwTaskActor> taskActors = request.getCopyUserIds().stream()
                .map(this::createTaskActor)
                .collect(Collectors.toList());

        // 使用 FlowLongEngine 的 createCcTask 方法创建抄送任务（自动获取 ProcessModel 和 NodeModel）
        flowLongEngine.createCcTask(task, taskActors, flowCreator);

        // 保存审批意见
        String userName = identityService.getUserName(currentUserId);
        String commentText = "任务抄送给：" + taskActors.stream()
                .map(FlwTaskActor::getActorName)
                .collect(Collectors.joining(", "));
        if (StrUtil.isNotBlank(request.getReason())) {
            commentText = commentText + "，原因：" + request.getReason();
        }
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.COPY.getCode(), commentText);

        log.info("任务抄送成功：taskId={}, copyUserIds={}", taskId, request.getCopyUserIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawTask(String taskId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);

        // 获取历史任务信息
        FlwHisTask hisTask = hisTaskMapper.selectById(id);
        if (hisTask == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 检查流程实例是否还在运行中
        FlwInstance activeInstance = instanceMapper.selectById(hisTask.getInstanceId());
        if (activeInstance == null) {
            throw new BusinessException(400, "流程已结束，无法撤回");
        }

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        taskService.withdrawTask(id, flowCreator);
        saveApprovalComment(hisTask, currentUserId, identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.WITHDRAW.getCode(), "任务撤回");

        log.info("任务撤回成功：taskId={}", taskId);
    }

    @Override
    public List<Map<String, String>> getChildTasks(String parentTaskId) {
        Long id = parseTaskId(parentTaskId);
        FlwTask task = queryService.getTask(id);
        if (task == null) {
            return Collections.emptyList();
        }
        // 当前节点同 taskKey 的全部活动任务（会签/票签每人一条）及其用户参与者，供减签选择
        List<FlwTask> nodeTasks = queryService.getTasksByInstanceIdAndTaskKey(task.getInstanceId(), task.getTaskKey());
        List<Map<String, String>> result = new ArrayList<>();
        for (FlwTask nodeTask : nodeTasks) {
            for (FlwTaskActor actor : queryService.getTaskActorsByTaskId(nodeTask.getId())) {
                if (!ActorType.user.eq(actor.getActorType())) {
                    continue;
                }
                Map<String, String> item = new HashMap<>();
                item.put("taskId", String.valueOf(nodeTask.getId()));
                item.put("actorId", actor.getActorId());
                item.put("actorName", actor.getActorName());
                item.put("performType", nodeTask.getPerformType() == null ? null : String.valueOf(nodeTask.getPerformType()));
                result.add(item);
            }
        }
        return result;
    }

    @Override
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

        List<FlwTask> approvalTasks = queryService.getTasksByInstanceId(instanceId).stream()
                .filter(t -> isHumanApprovalTask(t.getTaskType()))
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
            Long lastUrgeTime = readLastUrgeTime(task);
            if (lastUrgeTime != null && now - lastUrgeTime < throttleMillis) {
                earliestRetryMillis = Math.min(earliestRetryMillis, throttleMillis - (now - lastUrgeTime));
                continue;
            }
            urgeSingleTask(task, processName, urgerName, comment, now, currentUserId);
            urged++;
        }

        if (urged == 0) {
            long retryMinutes = Math.max(1, (earliestRetryMillis + 60_000 - 1) / 60_000);
            throw new BusinessException(400, "催办过于频繁，请约 " + retryMinutes + " 分钟后再试");
        }
        log.info("流程催办完成: instanceId={}, operator={}, urged={}/{}, comment={}",
                instanceId, currentUserId, urged, approvalTasks.size(), comment);
    }

    /**
     * 催办单个任务：写节流标记 + 通知处理人 + 审批意见留痕
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
        for (Long userId : actorUserIds) {
            try {
                notificationService.sendToUser(userId, NotificationMessage.workflow(title, content, task.getId()));
            } catch (Exception e) {
                log.warn("催办通知发送失败: taskId={}, userId={}", task.getId(), userId, e);
            }
        }

        // 审批意见留痕（挂在催办的任务上）
        saveApprovalComment(task, operatorUserId, urgerName,
                ApprovalActionTypeEnum.URGE.getCode(),
                StrUtil.isNotBlank(comment) ? "催办：" + comment : "催办任务「" + task.getTaskName() + "」");
    }

    /**
     * 是否需要人处理的审批任务（催办目标/抄送锚点共用判定）
     * 覆盖：审批(1)、条件审批(3，设计器条件审批节点独立使用时引擎按审批处理)、
     * 转办(10)、委派(11)——转办/委派后任务行 taskType 会被引擎改写但仍是待人审批；
     * 排除：发起人待重新提交(major)、抄送(cc，仅存历史表)、定时器/触发器/子流程等待、暂存
     */
    public static boolean isHumanApprovalTask(Integer taskType) {
        return TaskType.approval.eq(taskType)
                || TaskType.conditionNode.eq(taskType)
                || TaskType.transfer.eq(taskType)
                || TaskType.delegate.eq(taskType);
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

    private boolean hasWorkflowAdminRole() {
        UserContext context = UserContext.get();
        if (context == null) {
            return false;
        }
        if (context.isAdmin()) {
            return true;
        }
        if (context.getRoles() == null) {
            return false;
        }
        return context.getRoles().stream()
                .map(role -> role.getRoleCode() == null ? "" : role.getRoleCode().toLowerCase())
                .anyMatch(code -> "admin".equals(code) || "workflow_admin".equals(code));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reclaimTask(String taskId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }
        Long id = parseTaskId(taskId);
        FlwHisTask hisTask = hisTaskMapper.selectById(id);
        if (hisTask == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 仅该历史任务的办理人可拿回
        LambdaQueryWrapper<FlwHisTaskActor> actorWrapper = new LambdaQueryWrapper<>();
        actorWrapper.eq(FlwHisTaskActor::getTaskId, id);
        actorWrapper.eq(FlwHisTaskActor::getActorId, String.valueOf(currentUserId));
        if (hisTaskActorMapper.selectCount(actorWrapper) == 0) {
            throw new BusinessException(403, "只有该任务的办理人才能拿回");
        }

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        try {
            taskService.reclaimTask(id, flowCreator, null);
        } catch (com.aizuda.bpm.engine.exception.FlowLongException e) {
            log.info("拿回被引擎拒绝: taskId={}, reason={}", taskId, e.getMessage());
            throw new BusinessException(400, "下一节点已办理或当前任务不可拿回");
        }

        saveApprovalComment(hisTask, currentUserId, identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.RECLAIM.getCode(), "拿回任务「" + hisTask.getTaskName() + "」");
        log.info("任务拿回成功：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    public Page<TaskResponse> getCcTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userIdStr = String.valueOf(currentUserId);

        // 查询当前用户作为抄送人的历史任务参与者记录
        // 抄送人通过 weight = 6 来标识
        LambdaQueryWrapper<FlwHisTaskActor> actorWrapper = new LambdaQueryWrapper<>();
        actorWrapper.eq(FlwHisTaskActor::getActorId, userIdStr);
        actorWrapper.eq(FlwHisTaskActor::getWeight, 6); // 抄送人标识
        List<FlwHisTaskActor> ccTaskActors = hisTaskActorMapper.selectList(actorWrapper);

        if (ccTaskActors.isEmpty()) {
            Page<TaskResponse> emptyPage = new Page<>();
            emptyPage.setCurrent(request.getPageNum());
            emptyPage.setSize(request.getPageSize());
            emptyPage.setTotal(0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        // 获取所有抄送 taskId
        Set<Long> ccTaskIds = ccTaskActors.stream()
                .map(FlwHisTaskActor::getTaskId)
                .collect(Collectors.toSet());

        // 查询历史抄送任务
        LambdaQueryWrapper<FlwHisTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(FlwHisTask::getId, ccTaskIds);
        taskWrapper.eq(FlwHisTask::getTaskType, TaskType.cc.getValue()); // 抄送任务类型

        // 任务名称筛选
        if (StrUtil.isNotBlank(request.getName())) {
            taskWrapper.like(FlwHisTask::getTaskName, request.getName());
        }

        taskWrapper.orderByDesc(FlwHisTask::getCreateTime);

        Page<FlwHisTask> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwHisTask> taskPage = hisTaskMapper.selectPage(pageParam, taskWrapper);

        // 转换为响应对象，补充流程信息
        BatchContext hisCtx = preloadHisBatch(taskPage.getRecords());
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(t -> convertHisTaskToResponseWithProcess(t, hisCtx))
                .collect(Collectors.toList());

        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(taskPage.getCurrent());
        resultPage.setSize(taskPage.getSize());
        resultPage.setTotal(taskPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public List<TaskResponse> getCcTasksByInstanceId(String processInstanceId) {
        Long instanceId = parseTaskId(processInstanceId);

        // 使用 FlowLong 的查询服务获取流程实例的抄送任务参与者
        Optional<List<FlwHisTaskActor>> ccTaskActorsOpt = queryService.getCcTaskActorsByInstanceId(instanceId);

        if (ccTaskActorsOpt.isEmpty() || ccTaskActorsOpt.get().isEmpty()) {
            return Collections.emptyList();
        }

        List<FlwHisTaskActor> ccTaskActors = ccTaskActorsOpt.get();

        // 获取所有抄送 taskId
        Set<Long> ccTaskIds = ccTaskActors.stream()
                .map(FlwHisTaskActor::getTaskId)
                .collect(Collectors.toSet());

        // 查询历史抄送任务
        LambdaQueryWrapper<FlwHisTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(FlwHisTask::getId, ccTaskIds);
        taskWrapper.eq(FlwHisTask::getTaskType, TaskType.cc.getValue());
        taskWrapper.orderByDesc(FlwHisTask::getCreateTime);

        List<FlwHisTask> ccTasks = hisTaskMapper.selectList(taskWrapper);

        // 转换为响应对象
        BatchContext ccCtx = preloadHisBatch(ccTasks);
        return ccTasks.stream()
                .map(t -> convertHisTaskToResponseWithProcess(t, ccCtx))
                .collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    private Long parseTaskId(String taskId) {
        try {
            return Long.parseLong(taskId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "任务ID格式错误");
        }
    }

    private FlwTask validateTask(Long taskId) {
        FlwTask task = queryService.getTask(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在或已完成");
        }
        return task;
    }

    private FlowCreator createFlowCreator(Long userId) {
        return FlowCreatorFactory.user(userId, identityService.getUserName(userId));
    }

    private FlwTaskActor createTaskActor(Long userId) {
        FlwTaskActor taskActor = new FlwTaskActor();
        taskActor.setActorId(String.valueOf(userId));
        taskActor.setActorName(identityService.getUserName(userId));
        taskActor.setActorType(0); // 用户类型
        taskActor.setTenantId(FlowCreatorFactory.currentTenantId());
        return taskActor;
    }

    private void saveApprovalComment(FlwTask task, Long userId, String userName,
                                      String actionType, String commentText) {
        saveApprovalComment(task, userId, userName, actionType, commentText, null);
    }

    /**
     * 保存审批评论；带附件时落 attachment_ids 逗号串并将附件 biz 绑定到该评论
     */
    private void saveApprovalComment(FlwTask task, Long userId, String userName,
                                      String actionType, String commentText, List<Long> attachmentIds) {
        WfApprovalComment comment = new WfApprovalComment();
        comment.setProcessInstanceId(task.getInstanceId());
        comment.setTaskId(task.getId());
        comment.setTaskDefKey(task.getTaskKey());
        comment.setTaskName(task.getTaskName());
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setActionType(actionType);
        comment.setCommentText(commentText);
        comment.setCreateTime(LocalDateTime.now());
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            comment.setAttachmentIds(attachmentIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        approvalCommentMapper.insert(comment);

        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            sysAttachmentService.lambdaUpdate()
                    .set(SysAttachment::getBizType, COMMENT_ATTACHMENT_BIZ_TYPE)
                    .set(SysAttachment::getBizId, comment.getId())
                    .in(SysAttachment::getId, attachmentIds)
                    .and(w -> w.isNull(SysAttachment::getBizId).or().eq(SysAttachment::getBizId, comment.getId()))
                    .update();
        }
    }

    private TaskResponse convertTaskToResponse(FlwTask task) {
        TaskResponse response = new TaskResponse();
        response.setId(String.valueOf(task.getId()));
        response.setName(task.getTaskName());
        response.setTaskDefinitionKey(task.getTaskKey());
        response.setProcessInstanceId(String.valueOf(task.getInstanceId()));

        // 受理人以实际参与者为准（转办/并行加签不更新任务创建人字段，
        // 直接读 create_by 会显示转办前的处理人）；多参与人时回退为待认领+名单展示
        List<FlwTaskActor> actors = queryService.getTaskActorsByTaskId(task.getId());
        List<FlwTaskActor> userActors = actors.stream()
                .filter(a -> ActorType.user.eq(a.getActorType()))
                .toList();
        if (userActors.size() == 1) {
            response.setAssignee(userActors.get(0).getActorId());
            response.setAssigneeName(userActors.get(0).getActorName());
        } else {
            // 多用户参与（或签/并行加签后）或角色部门候选池：待认领 + 参与者名单
            response.setCandidate(true);
            response.setCandidateUsers(actors.stream()
                    .map(FlwTaskActor::getActorName)
                    .distinct()
                    .collect(Collectors.toList()));
            if (!userActors.isEmpty()) {
                response.setAssignee(userActors.get(0).getActorId());
                response.setAssigneeName(userActors.get(0).getActorName());
            } else if (task.getCreateId() != null) {
                response.setAssignee(task.getCreateId());
                response.setAssigneeName(task.getCreateBy());
            }
        }

        response.setCreateTime(task.getCreateTime());
        return response;
    }

    /**
     * 转换任务响应（包含流程信息）
     */
    private TaskResponse convertTaskToResponseWithProcess(FlwTask task) {
        TaskResponse response = convertTaskToResponse(task);

        // 补充流程实例信息
        FlwInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance != null) {
            response.setProcessInstanceId(String.valueOf(instance.getId()));
            response.setUrgent(instance.getUrgent());
            response.setInstanceTitle(InstanceTitleRenderer.extractTitle(instance.getVariable()));

            // 补充流程定义信息
            FlwProcess process = processService.getProcessById(instance.getProcessId());
            if (process != null) {
                response.setProcessDefinitionId(String.valueOf(process.getId()));
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }

            // 发起人信息
            if (StrUtil.isNotBlank(instance.getCreateId())) {
                response.setStartUserId(instance.getCreateId());
                try {
                    Long startUserId = Long.parseLong(instance.getCreateId());
                    response.setStartUserName(identityService.getUserName(startUserId));
                } catch (NumberFormatException e) {
                    response.setStartUserName(instance.getCreateBy());
                }
            }

            response.setStartTime(instance.getCreateTime());

            // 流程编号
            response.setProcessNo(instance.getInstanceNo());
        }

        return response;
    }

    private TaskResponse convertHisTaskToResponse(FlwHisTask hisTask) {
        TaskResponse response = new TaskResponse();
        response.setId(String.valueOf(hisTask.getId()));
        response.setName(hisTask.getTaskName());
        response.setTaskDefinitionKey(hisTask.getTaskKey());
        response.setProcessInstanceId(String.valueOf(hisTask.getInstanceId()));

        if (hisTask.getCreateId() != null) {
            try {
                response.setAssignee(hisTask.getCreateId());
                response.setAssigneeName(identityService.getUserName(Long.parseLong(hisTask.getCreateId())));
            } catch (NumberFormatException ignored) {}
        }

        response.setCreateTime(hisTask.getCreateTime());
        response.setEndTime(hisTask.getFinishTime());
        return response;
    }

    /**
     * 转换历史任务响应（包含流程信息）
     */

    /**
     * 标题过滤无命中的空页快速返回
     */
    private Page<TaskResponse> emptyTaskPage(TaskQueryRequest request) {
        Page<TaskResponse> empty = new Page<>(request.getPageNum(), request.getPageSize());
        empty.setTotal(0);
        empty.setRecords(Collections.emptyList());
        return empty;
    }

    /**
     * 标题/模糊关键字转义：以 | 为转义符（MySQL/H2 双兼容），% _ 按字面匹配；空白返回 null
     */
    private String escapeLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .replace("|", "||")
                .replace("%", "|%")
                .replace("_", "|_");
    }
}
