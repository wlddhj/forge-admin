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

        taskWrapper.orderByDesc(FlwTask::getCreateTime);

        Page<FlwTask> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwTask> taskPage = taskMapper.selectPage(pageParam, taskWrapper);

        // 本人从候选组（角色/部门）签收的任务标记，供前端展示“取消签收”
        Set<Long> claimedFromGroupTaskIds = taskActors.stream()
                .filter(a -> AgentType.claimRole.eq(a.getAgentType()) || AgentType.claimDepartment.eq(a.getAgentType()))
                .map(FlwTaskActor::getTaskId)
                .collect(Collectors.toSet());

        // 4. 转换为响应对象，补充流程信息
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(task -> {
                    TaskResponse response = convertTaskToResponseWithProcess(task);
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
        Set<String> groupIds = new HashSet<>();
        identityService.getRoleIdsByUserId(currentUserId).forEach(groupIds::add);
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

        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(task -> {
                    TaskResponse response = convertTaskToResponseWithProcess(task);
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

        // 任务名称筛选
        if (StrUtil.isNotBlank(request.getName())) {
            taskWrapper.like(FlwHisTask::getTaskName, request.getName());
        }

        taskWrapper.orderByDesc(FlwHisTask::getFinishTime);

        Page<FlwHisTask> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwHisTask> taskPage = hisTaskMapper.selectPage(pageParam, taskWrapper);

        // 4. 转换为响应对象，补充流程信息
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(this::convertHisTaskToResponseWithProcess)
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

        // 驳回任务
        FlowCreator flowCreator = createFlowCreator(currentUserId);
//        taskService.rejectTask(task, flowCreator, request.getVariables());
        flowLongEngine.executeRejectTask(task, null, flowCreator, request.getVariables(), true);
        log.info("任务审批驳回：taskId={}, userId={}", taskId, currentUserId);
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
                List<FlwTaskActor> taskActors = request.getUserIds().stream()
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
                .filter(t -> TaskType.approval.eq(t.getTaskType()))
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
        List<TaskResponse> records = taskPage.getRecords().stream()
                .map(this::convertHisTaskToResponseWithProcess)
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
        return ccTasks.stream()
                .map(this::convertHisTaskToResponseWithProcess)
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

        if (task.getCreateId() != null) {
            try {
                response.setAssignee(task.getCreateId());
                response.setAssigneeName(identityService.getUserName(Long.parseLong(task.getCreateId())));
            } catch (NumberFormatException ignored) {}
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
    private TaskResponse convertHisTaskToResponseWithProcess(FlwHisTask hisTask) {
        TaskResponse response = convertHisTaskToResponse(hisTask);

        // 补充流程实例信息
        FlwHisInstance hisInstance = hisInstanceMapper.selectById(hisTask.getInstanceId());
        if (hisInstance != null) {
            response.setProcessInstanceId(String.valueOf(hisInstance.getId()));

            // 补充流程定义信息
            FlwProcess process = processService.getProcessById(hisInstance.getProcessId());
            if (process != null) {
                response.setProcessDefinitionId(String.valueOf(process.getId()));
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }

            // 发起人信息
            if (StrUtil.isNotBlank(hisInstance.getCreateId())) {
                response.setStartUserId(hisInstance.getCreateId());
                try {
                    Long startUserId = Long.parseLong(hisInstance.getCreateId());
                    response.setStartUserName(identityService.getUserName(startUserId));
                } catch (NumberFormatException e) {
                    response.setStartUserName(hisInstance.getCreateBy());
                }
            }

            response.setStartTime(hisInstance.getCreateTime());

            // 流程编号
            response.setProcessNo(hisInstance.getInstanceNo());

        }

        // 补充审批意见信息：动作、审批意见、审批时间
        LambdaQueryWrapper<WfApprovalComment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(WfApprovalComment::getTaskId, hisTask.getId());
        commentWrapper.orderByDesc(WfApprovalComment::getCreateTime);
        commentWrapper.last("LIMIT 1");
        WfApprovalComment approvalComment = approvalCommentMapper.selectOne(commentWrapper);
        if (approvalComment != null) {
            response.setActionType(approvalComment.getActionType());
            response.setCommentText(approvalComment.getCommentText());
            // 审批时间取审批意见表的创建时间
            if (approvalComment.getCreateTime() != null) {
                response.setEndTime(Date.from(approvalComment.getCreateTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }
        }

        response.setWithdrawn(
                hisTask.getTaskType().equals(TaskType.withdraw.getValue())
                ||hisTask.getTaskType().equals(TaskType.saveAsDraft.getValue())
                || ApprovalActionTypeEnum.WITHDRAW.getCode().equals(response.getActionType())
        );

        // 补充下一节点名称：查找当前任务完成后，下一个创建的任务
        if (hisTask.getFinishTime() != null) {
            LambdaQueryWrapper<FlwHisTask> nextTaskWrapper = new LambdaQueryWrapper<>();
            nextTaskWrapper.eq(FlwHisTask::getInstanceId, hisTask.getInstanceId());
            nextTaskWrapper.gt(FlwHisTask::getCreateTime, hisTask.getFinishTime());
            nextTaskWrapper.orderByAsc(FlwHisTask::getCreateTime);
            nextTaskWrapper.last("LIMIT 1");
            FlwHisTask nextTask = hisTaskMapper.selectOne(nextTaskWrapper);
            if (nextTask != null) {
                response.setNextActivityName(nextTask.getTaskName());
            }
        }

        return response;
    }
}