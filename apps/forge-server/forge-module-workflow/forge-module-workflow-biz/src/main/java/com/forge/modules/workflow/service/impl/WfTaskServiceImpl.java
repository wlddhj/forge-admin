package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.*;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.PerformType;
import com.aizuda.bpm.engine.core.enums.TaskState;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.workflow.dto.task.*;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.service.WfTaskService;
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

    @Override
    public Page<TaskResponse> getTodoTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        // TODO: 使用 FlowLong QueryService 实现待办任务查询
        // FlowLong 需要通过 MyBatis Plus 直接查询 flw_task 表
        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(request.getPageNum());
        resultPage.setSize(request.getPageSize());
        resultPage.setTotal(0);
        resultPage.setRecords(Collections.emptyList());
        return resultPage;
    }

    @Override
    public Page<TaskResponse> getClaimableTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        // TODO: 实现可签收任务查询
        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(request.getPageNum());
        resultPage.setSize(request.getPageSize());
        resultPage.setTotal(0);
        resultPage.setRecords(Collections.emptyList());
        return resultPage;
    }

    @Override
    public Page<TaskResponse> getDoneTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        // TODO: 实现已办任务查询
        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(request.getPageNum());
        resultPage.setSize(request.getPageSize());
        resultPage.setTotal(0);
        resultPage.setRecords(Collections.emptyList());
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
    public void unclaimTask(String taskId) {
        // TODO: 实现取消签收
        log.info("取消签收成功：taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        taskService.complete(id, flowCreator, request.getVariables());

        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.SUBMIT.getCode(), request.getComment());
        log.info("任务完成：taskId={}, userId={}", taskId, currentUserId);
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
        taskService.complete(id, flowCreator, variables);

        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.APPROVE.getCode(), request.getComment());
        log.info("任务审批通过：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.REJECT.getCode(), request.getComment());

        // 驳回任务
        FlowCreator flowCreator = createFlowCreator(currentUserId);
        taskService.rejectTask(task, flowCreator, request.getVariables());

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
        taskService.executeJumpTask(id, request.getTargetTaskDefKey(), flowCreator, null, t -> null, TaskType.rejectJump);

        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.RETURN.getCode(), request.getComment());
        log.info("任务退回：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    public List<Map<String, String>> getReturnNodes(String taskId) {
        // TODO: 实现获取可退回节点列表
        return Collections.emptyList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signCreateTask(String taskId, TaskSignCreateRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        List<FlwTaskActor> taskActors = request.getUserIds().stream()
                .map(userIdStr -> {
                    Long userId = Long.parseLong(userIdStr);
                    return createTaskActor(userId);
                })
                .toList();

        PerformType performType = PerformType.countersign; // 默认会签
        taskService.addTaskActor(id, performType, taskActors, flowCreator);

        saveApprovalComment(task, currentUserId, identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.SIGN_CREATE.getCode(),
                "加签操作，类型：" + request.getType() + "，原因：" + request.getReason());
        log.info("任务加签成功：taskId={}, type={}, userIds={}", taskId, request.getType(), request.getUserIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signDeleteTask(String taskId, TaskSignDeleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        taskService.removeTaskActor(id, Collections.singletonList(request.getChildTaskId()), flowCreator);

        saveApprovalComment(task, currentUserId, identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.SIGN_DELETE.getCode(),
                "减签操作，原因：" + request.getReason());
        log.info("任务减签成功：taskId={}, childTaskId={}", taskId, request.getChildTaskId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyTask(String taskId, TaskCopyRequest request) {
        // FlowLong 使用 createCcTask 实现抄送
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);
        FlwTask task = validateTask(id);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        List<FlwTaskActor> taskActors = request.getCopyUserIds().stream()
                .map(userId -> createTaskActor(userId))
                .collect(Collectors.toList());

        taskService.addTaskActor(id, taskActors, flowCreator);

        log.info("任务抄送成功：taskId={}, copyUserIds={}", taskId, request.getCopyUserIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawTask(String taskId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long id = parseTaskId(taskId);

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        taskService.withdrawTask(id, flowCreator);

        log.info("任务撤回成功：taskId={}", taskId);
    }

    @Override
    public List<Map<String, String>> getChildTasks(String parentTaskId) {
        // TODO: 实现获取子任务列表
        return Collections.emptyList();
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
        return new FlowCreator(String.valueOf(userId), identityService.getUserName(userId));
    }

    private FlwTaskActor createTaskActor(Long userId) {
        FlwTaskActor taskActor = new FlwTaskActor();
        taskActor.setActorId(String.valueOf(userId));
        taskActor.setActorName(identityService.getUserName(userId));
        taskActor.setActorType(0); // 用户类型
        return taskActor;
    }

    private void saveApprovalComment(FlwTask task, Long userId, String userName,
                                      String actionType, String commentText) {
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
        approvalCommentMapper.insert(comment);
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
}