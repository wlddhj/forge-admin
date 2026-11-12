package com.forge.modules.workflow.service.impl;

import com.forge.modules.workflow.framework.task.WfUrgeService;

import com.forge.modules.workflow.framework.task.TaskResponseAssembler;

import com.forge.modules.workflow.framework.task.TaskBatchLoader;

import com.forge.modules.workflow.framework.task.WfTaskQueryService;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.*;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.AgentType;
import com.aizuda.bpm.engine.core.enums.PerformType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.engine.model.ModelHelper;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.dto.task.*;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.framework.actor.ForgeTaskAccessStrategy;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.service.WfTaskService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流任务管理服务实现 - FlowLong 版本
 * <p>
 * 上帝类拆分第四批后的门面：动作方法群（审批/驳回/签收/委派/转办/退回/加减签/抄送/撤回/拿回）
 * 与归属校验保留本类；四列表查询委托 {@link WfTaskQueryService}，批量预取委托 {@link TaskBatchLoader}，
 * 响应组装委托 {@link TaskResponseAssembler}，催办委托 {@link WfUrgeService}。
 *
 * @author forge-admin
 */
@Slf4j
@Service("wfTaskService")
@RequiredArgsConstructor
public class WfTaskServiceImpl implements WfTaskService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final QueryService queryService;
    private final FlowLongIdentityService identityService;
    private final WfApprovalCommentMapper approvalCommentMapper;
    private final FlwHisTaskMapper hisTaskMapper;
    private final FlwHisTaskActorMapper hisTaskActorMapper;
    private final FlwInstanceMapper instanceMapper;
    private final FlowLongEngine flowLongEngine;
    private final SysAttachmentService sysAttachmentService;
    private final ObjectMapper objectMapper;
    private final ForgeTaskAccessStrategy taskAccessStrategy;
    private final WfTaskQueryService wfTaskQueryService;
    private final TaskResponseAssembler taskResponseAssembler;
    private final WfUrgeService wfUrgeService;

    private static final String COMMENT_ATTACHMENT_BIZ_TYPE = "wf_comment";

    @Override
    public Page<TaskResponse> getTodoTasks(TaskQueryRequest request) {
        return wfTaskQueryService.getTodoTasks(request);
    }

    @Override
    public Page<TaskResponse> getClaimableTasks(TaskQueryRequest request) {
        return wfTaskQueryService.getClaimableTasks(request);
    }

    @Override
    public Page<TaskResponse> getDoneTasks(TaskQueryRequest request) {
        return wfTaskQueryService.getDoneTasks(request);
    }

    @Override
    public Page<TaskResponse> getCcTasks(TaskQueryRequest request) {
        return wfTaskQueryService.getCcTasks(request);
    }

    @Override
    public List<TaskResponse> getCcTasksByInstanceId(String processInstanceId) {
        return wfTaskQueryService.getCcTasksByInstanceId(parseTaskId(processInstanceId));
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
            return taskResponseAssembler.convertHisTaskToResponse(hisTask);
        }
        return taskResponseAssembler.convertTaskToResponse(task);
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
        assertTaskOperable(task, currentUserId);

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
        assertTaskOperable(task, currentUserId);

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
        assertTaskOperable(task, currentUserId);

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
        assertTaskOperable(task, currentUserId);

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
        assertTaskOperable(task, currentUserId);

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
        assertTaskOperable(task, currentUserId);

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
        assertTaskOperable(task, currentUserId);

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
        assertHisTaskOperable(hisTask, currentUserId);

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
        // 催办（含 FOR UPDATE 行锁节流/通知/留痕）已收敛至 WfUrgeService
        wfUrgeService.urgeInstance(processInstanceId, comment);
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

    // ========== 私有方法 ==========

    /**
     * 是否需要人处理的审批任务（催办目标/抄送锚点共用判定）。
     * 实现已收敛至 {@link TaskResponseAssembler#isHumanApprovalTask}；
     * WfProcessInstanceCopyServiceImpl 等外部调用方仍经本入口静态引用，保留契约。
     */
    public static boolean isHumanApprovalTask(Integer taskType) {
        return TaskResponseAssembler.isHumanApprovalTask(taskType);
    }

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

    /**
     * 校验当前用户有权操作该活动任务：直系参与者/候选组隶属者本人，工作流管理员豁免。
     * 引擎侧 reject/退回/撤回/加减签/抄送/委派/转办等路径不做参与者校验（isAllowed 仅覆盖 complete 系），
     * 服务层必须统一把关，防止越权操作他人任务。
     */
    private void assertTaskOperable(FlwTask task, Long currentUserId) {
        List<FlwTaskActor> actors = queryService.getTaskActorsByTaskId(task.getId());
        if (taskAccessStrategy.isAllowed(String.valueOf(currentUserId), actors) == null) {
            throw new BusinessException(403, "无权操作该任务");
        }
    }

    /**
     * 撤回场景校验：历史任务的处理人本人（历史参与者表），工作流管理员豁免
     */
    private void assertHisTaskOperable(FlwHisTask hisTask, Long currentUserId) {
        List<FlwHisTaskActor> hisActors = hisTaskActorMapper.selectList(
                new LambdaQueryWrapper<FlwHisTaskActor>().eq(FlwHisTaskActor::getTaskId, hisTask.getId()));
        List<FlwTaskActor> actors = hisActors == null ? List.of() : new ArrayList<>(hisActors);
        if (taskAccessStrategy.isAllowed(String.valueOf(currentUserId), actors) == null) {
            throw new BusinessException(403, "无权操作该任务");
        }
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
}
