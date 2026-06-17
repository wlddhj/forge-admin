package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.workflow.dto.task.*;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.entity.WfProcessInstanceCopy;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.identity.FlowableIdentityService;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.mapper.WfProcessInstanceCopyMapper;
import com.forge.modules.workflow.service.WfProcessInstanceCopyService;
import com.forge.modules.workflow.service.WfTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流任务管理服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfTaskServiceImpl implements WfTaskService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final FlowableIdentityService flowableIdentityService;
    private final WfApprovalCommentMapper wfApprovalCommentMapper;
    private final WfProcessInstanceCopyMapper wfProcessInstanceCopyMapper;
    private final WfProcessInstanceCopyService copyService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<TaskResponse> getTodoTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userId = String.valueOf(currentUserId);
        var query = taskService.createTaskQuery()
                .or()
                .taskAssignee(userId)
                .taskCandidateUser(userId)
                .endOr()
                .includeProcessVariables()
                .orderByTaskCreateTime().desc();

        buildTaskQueryConditions(query, request);

        long total = query.count();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        List<Task> tasks = query.listPage(offset, request.getPageSize());

        return buildTaskPage(tasks, total, request.getPageNum(), request.getPageSize());
    }

    @Override
    public Page<TaskResponse> getClaimableTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        var query = taskService.createTaskQuery()
                .taskCandidateUser(String.valueOf(currentUserId))
                .taskUnassigned()
                .orderByTaskCreateTime().desc();

        buildTaskQueryConditions(query, request);

        long total = query.count();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        List<Task> tasks = query.listPage(offset, request.getPageSize());

        return buildTaskPage(tasks, total, request.getPageNum(), request.getPageSize());
    }

    @Override
    public Page<TaskResponse> getDoneTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userId = String.valueOf(currentUserId);
        var query = historyService.createHistoricTaskInstanceQuery()
                .finished()
                .or()
                .taskAssignee(userId)
                .taskCandidateUser(userId)
                .endOr()
                .includeProcessVariables()
                .orderByHistoricTaskInstanceEndTime().desc();

        buildHistoricTaskQueryConditions(query, request);

        long total = query.count();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        List<HistoricTaskInstance> tasks = query.listPage(offset, request.getPageSize());

        Map<String, ProcessDefinition> definitionCache = batchLoadHistoricProcessDefinitions(tasks);
        Map<Long, String> userNameCache = batchLoadHistoricUserNames(tasks);

        List<TaskResponse> records = tasks.stream()
                .map(task -> convertHistoricTaskToResponse(task, definitionCache, userNameCache))
                .collect(Collectors.toList());

        // 批量加载审批意见
        if (!tasks.isEmpty()) {
            List<String> taskIds = tasks.stream().map(HistoricTaskInstance::getId).toList();
            LambdaQueryWrapper<WfApprovalComment> commentWrapper = new LambdaQueryWrapper<>();
            commentWrapper.in(WfApprovalComment::getTaskId, taskIds)
                    .orderByDesc(WfApprovalComment::getCreateTime);
            List<WfApprovalComment> comments = wfApprovalCommentMapper.selectList(commentWrapper);
            Map<String, WfApprovalComment> commentMap = new LinkedHashMap<>();
            for (WfApprovalComment c : comments) {
                commentMap.putIfAbsent(c.getTaskId(), c);
            }
            for (int i = 0; i < records.size(); i++) {
                WfApprovalComment comment = commentMap.get(tasks.get(i).getId());
                if (comment != null) {
                    records.get(i).setActionType(comment.getActionType());
                    records.get(i).setCommentText(comment.getCommentText());
                }
            }
        }

        // 批量加载下一节点名称
        if (!tasks.isEmpty()) {
            Set<String> processInstanceIds = tasks.stream()
                    .map(HistoricTaskInstance::getProcessInstanceId)
                    .collect(Collectors.toSet());
            // 批量查询所有流程实例的历史任务，按开始时间排序
            Map<String, List<HistoricTaskInstance>> tasksByProcess = new HashMap<>();
            for (String pId : processInstanceIds) {
                List<HistoricTaskInstance> pTasks = historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(pId)
                        .orderByHistoricTaskInstanceStartTime().asc()
                        .list();
                tasksByProcess.put(pId, pTasks);
            }
            for (int i = 0; i < records.size(); i++) {
                HistoricTaskInstance current = tasks.get(i);
                List<HistoricTaskInstance> pTasks = tasksByProcess.get(current.getProcessInstanceId());
                if (pTasks != null && current.getEndTime() != null) {
                    // 找到当前任务在列表中的位置，下一个就是后续节点
                    for (int j = 0; j < pTasks.size(); j++) {
                        if (pTasks.get(j).getId().equals(current.getId())) {
                            if (j + 1 < pTasks.size()) {
                                records.get(i).setNextActivityName(pTasks.get(j + 1).getName());
                            }
                            break;
                        }
                    }
                }
            }
        }

        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(request.getPageNum());
        resultPage.setSize(request.getPageSize());
        resultPage.setTotal(total);
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public TaskResponse getTaskById(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .includeProcessVariables()
                .singleResult();

        if (task != null) {
            Map<String, ProcessDefinition> defCache = batchLoadProcessDefinitions(List.of(task));
            Map<Long, String> userCache = batchLoadUserNames(List.of(task));
            return convertTaskToResponse(task, defCache, userCache);
        }

        // 如果运行时任务不存在，尝试从历史记录获取
        HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .includeProcessVariables()
                .singleResult();

        if (historicTask == null) {
            throw new BusinessException(404, "任务不存在");
        }

        Map<String, ProcessDefinition> defCache = batchLoadHistoricProcessDefinitions(List.of(historicTask));
        Map<Long, String> userCache = batchLoadHistoricUserNames(List.of(historicTask));
        return convertHistoricTaskToResponse(historicTask, defCache, userCache);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimTask(String taskId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        Task task = validateTask(taskId);

        // 检查当前用户是否是候选人
        if (task.getAssignee() != null) {
            throw new BusinessException(400, "该任务已被其他人签收");
        }

        taskService.claim(taskId, String.valueOf(currentUserId));

        log.info("任务签收成功：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    public void unclaimTask(String taskId) {
        taskService.unclaim(taskId);
        log.info("取消签收成功：taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        // 完成任务
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            taskService.complete(taskId, request.getVariables());
        } else {
            taskService.complete(taskId);
        }

        // 保存审批意见
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.SUBMIT.getCode(), request.getComment());

        log.info("任务完成：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        // 设置审批通过变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", true);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }

        taskService.complete(taskId, variables);

        // 保存审批通过意见
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.APPROVE.getCode(), request.getComment());

        log.info("任务审批通过：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        // 保存审批驳回意见
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.REJECT.getCode(), request.getComment());

        // 驳回时直接终止流程实例，设置 deleteReason 使前端显示"已终止"
        String processInstanceId = task.getProcessInstanceId();
        String processDefinitionId = task.getProcessDefinitionId();
        String reason = request.getComment() != null ? request.getComment() : "审批驳回";
        runtimeService.deleteProcessInstance(processInstanceId, reason);

        // 自动抄送
        try {
            copyService.autoCopyOnProcessEnd(processInstanceId, processDefinitionId, "流程驳回自动抄送");
        } catch (Exception e) {
            log.warn("流程驳回自动抄送异常：processInstanceId={}", processInstanceId, e);
        }

        log.info("任务审批驳回（流程终止）：taskId={}, processInstanceId={}, userId={}", taskId, processInstanceId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(String taskId, TaskDelegateRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        String delegateUserId = String.valueOf(request.getDelegateUserId());

        // 委派任务：记录原处理人为 owner，设置被委派人为新 assignee
        // 不使用 taskService.delegateTask()，因为它会产生 PENDING delegation state，
        // 导致被委派人无法直接 complete 任务
        taskService.setOwner(taskId, String.valueOf(currentUserId));
        taskService.setAssignee(taskId, delegateUserId);

        // 保存委派意见
        String delegateUserName = flowableIdentityService.getUserName(request.getDelegateUserId());
        String commentText = StrUtil.isNotBlank(request.getComment())
                ? request.getComment()
                : "任务委派给：" + delegateUserName;
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.DELEGATE.getCode(), commentText);

        log.info("任务委派：taskId={}, fromUser={}, toUser={}", taskId, currentUserId, delegateUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(String taskId, TaskTransferRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        String transferUserId = String.valueOf(request.getTransferUserId());

        // 转办任务（直接设置处理人）
        taskService.setAssignee(taskId, transferUserId);

        // 保存转办意见
        String transferUserName = flowableIdentityService.getUserName(request.getTransferUserId());
        String commentText = StrUtil.isNotBlank(request.getComment())
                ? request.getComment()
                : "任务转办给：" + transferUserName;
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.TRANSFER.getCode(), commentText);

        log.info("任务转办：taskId={}, fromUser={}, toUser={}", taskId, currentUserId, transferUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(String taskId, TaskReturnRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        String currentActivityId = task.getTaskDefinitionKey();
        String targetActivityId = request.getTargetTaskDefKey();

        // 验证目标节点是否可以退回
        List<Map<String, String>> returnNodes = getReturnNodes(taskId);
        boolean isValidTarget = returnNodes.stream()
                .anyMatch(node -> targetActivityId.equals(node.get("taskDefKey")));
        if (!isValidTarget) {
            throw new BusinessException(400, "目标节点不在可退回列表中");
        }

        // 执行退回操作
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(currentActivityId, targetActivityId)
                .changeState();

        // 保存退回意见
        saveApprovalComment(task, currentUserId, userName, ApprovalActionTypeEnum.RETURN.getCode(), request.getComment());

        log.info("任务退回：taskId={}, fromNode={}, toNode={}, userId={}",
                taskId, currentActivityId, targetActivityId, currentUserId);
    }

    @Override
    public List<Map<String, String>> getReturnNodes(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        String processInstanceId = task.getProcessInstanceId();

        // 获取当前任务之前已完成的用户任务
        List<HistoricActivityInstance> completedActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .finished()
                .orderByHistoricActivityInstanceEndTime().asc()
                .list();

        // 去重，保留每个节点的最新记录
        Map<String, HistoricActivityInstance> activityMap = new LinkedHashMap<>();
        for (HistoricActivityInstance activity : completedActivities) {
            activityMap.put(activity.getActivityId(), activity);
        }

        // 过滤掉当前节点，构建返回结果
        List<Map<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, HistoricActivityInstance> entry : activityMap.entrySet()) {
            if (!entry.getKey().equals(task.getTaskDefinitionKey())) {
                HistoricActivityInstance activity = entry.getValue();
                Map<String, String> node = new HashMap<>();
                node.put("taskDefKey", activity.getActivityId());
                node.put("taskName", activity.getActivityName());
                result.add(node);
            }
        }

        return result;
    }

    // ========== 私有方法 ==========

    /**
     * 验证任务存在且未完成
     */
    private Task validateTask(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null) {
            throw new BusinessException(404, "任务不存在或已完成");
        }
        return task;
    }

    /**
     * 验证当前用户是任务的处理人
     */
    private void validateTaskAssignee(Task task, Long currentUserId) {
        String userId = String.valueOf(currentUserId);
        // 候选任务：先认领
        if (task.getAssignee() == null) {
            // 检查是否为候选人
            try {
                boolean isCandidate = taskService.getIdentityLinksForTask(task.getId()).stream()
                        .anyMatch(link -> "candidate".equals(link.getType()) && userId.equals(link.getUserId()));
                if (isCandidate) {
                    taskService.claim(task.getId(), userId);
                    task.setAssignee(userId);
                } else {
                    throw new BusinessException(403, "当前用户不是该任务的处理人");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(403, "当前用户不是该任务的处理人");
            }
        } else if (!task.getAssignee().equals(userId)) {
            throw new BusinessException(403, "当前用户不是该任务的处理人");
        }
        // claim 会正确写入 ACT_HI_TASKINST.ASSIGNEE_，而 setAssignee 会产生 delegation state
        // 候选任务已在上方通过 claim 处理，已指派任务的 assignee 由 BpmTaskCandidateListener 设置
    }

    /**
     * 构建运行时任务查询条件
     */
    private void buildTaskQueryConditions(org.flowable.task.api.TaskQuery query, TaskQueryRequest request) {
        if (StrUtil.isNotBlank(request.getName())) {
            query.taskNameLike("%" + request.getName() + "%");
        }
    }

    /**
     * 构建历史任务查询条件
     */
    private void buildHistoricTaskQueryConditions(org.flowable.task.api.history.HistoricTaskInstanceQuery query,
                                                    TaskQueryRequest request) {
        if (StrUtil.isNotBlank(request.getName())) {
            query.taskNameLike("%" + request.getName() + "%");
        }
    }

    /**
     * 构建运行时任务分页结果（批量预加载关联数据，避免 N+1 查询）
     */
    private Page<TaskResponse> buildTaskPage(List<Task> tasks, long total, int pageNum, int pageSize) {
        if (tasks.isEmpty()) {
            Page<TaskResponse> resultPage = new Page<>();
            resultPage.setCurrent(pageNum);
            resultPage.setSize(pageSize);
            resultPage.setTotal(total);
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        Map<String, ProcessDefinition> definitionCache = batchLoadProcessDefinitions(tasks);
        Map<Long, String> userNameCache = batchLoadUserNames(tasks);

        List<TaskResponse> records = tasks.stream()
                .map(task -> convertTaskToResponse(task, definitionCache, userNameCache))
                .collect(Collectors.toList());

        Page<TaskResponse> resultPage = new Page<>();
        resultPage.setCurrent(pageNum);
        resultPage.setSize(pageSize);
        resultPage.setTotal(total);
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 将运行时任务转换为响应对象（使用缓存的流程定义和用户名）
     */
    private TaskResponse convertTaskToResponse(Task task,
                                                Map<String, ProcessDefinition> definitionCache,
                                                Map<Long, String> userNameCache) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setName(task.getName());
        response.setTaskDefinitionKey(task.getTaskDefinitionKey());
        response.setProcessInstanceId(task.getProcessInstanceId());

        ProcessDefinition definition = definitionCache.get(task.getProcessInstanceId());
        if (definition != null) {
            response.setProcessDefinitionName(definition.getName());
        }

        // 流程编号
        Map<String, Object> processVariables = task.getProcessVariables();
        if (processVariables != null && processVariables.get("processNo") != null) {
            response.setProcessNo(processVariables.get("processNo").toString());
        }

        response.setAssignee(task.getAssignee());
        if (StrUtil.isNotBlank(task.getAssignee())) {
            try {
                Long assigneeId = Long.parseLong(task.getAssignee());
                response.setAssigneeName(userNameCache.getOrDefault(assigneeId, task.getAssignee()));
            } catch (NumberFormatException e) {
                response.setAssigneeName(task.getAssignee());
            }
        } else {
            response.setCandidate(true);
            // 查询候选用户
            try {
                List<String> candidateNames = taskService.getIdentityLinksForTask(task.getId())
                        .stream()
                        .filter(link -> "candidate".equals(link.getType()) && link.getUserId() != null)
                        .map(link -> {
                            try {
                                Long uid = Long.parseLong(link.getUserId());
                                return userNameCache.getOrDefault(uid, link.getUserId());
                            } catch (NumberFormatException e) {
                                return link.getUserId();
                            }
                        })
                        .collect(Collectors.toList());
                response.setCandidateUsers(candidateNames);
            } catch (Exception e) {
                log.warn("查询候选用户失败: taskId={}", task.getId());
            }
        }

        response.setOwner(task.getOwner());
        if (StrUtil.isNotBlank(task.getOwner())) {
            try {
                Long ownerId = Long.parseLong(task.getOwner());
                response.setOwnerName(userNameCache.getOrDefault(ownerId, task.getOwner()));
            } catch (NumberFormatException e) {
                response.setOwnerName(task.getOwner());
            }
        }

        if (task.getCreateTime() != null) {
            response.setCreateTime(task.getCreateTime());
        }
        if (task.getClaimTime() != null) {
            response.setClaimTime(task.getClaimTime());
        }
        if (task.getDueDate() != null) {
            response.setDueDate(task.getDueDate());
        }
        response.setCategory(task.getCategory());

        return response;
    }

    /**
     * 将历史任务转换为响应对象（使用缓存的流程定义和用户名）
     */
    private TaskResponse convertHistoricTaskToResponse(HistoricTaskInstance historicTask,
                                                        Map<String, ProcessDefinition> definitionCache,
                                                        Map<Long, String> userNameCache) {
        TaskResponse response = new TaskResponse();
        response.setId(historicTask.getId());
        response.setName(historicTask.getName());
        response.setTaskDefinitionKey(historicTask.getTaskDefinitionKey());
        response.setProcessInstanceId(historicTask.getProcessInstanceId());

        ProcessDefinition definition = definitionCache.get(historicTask.getProcessInstanceId());
        if (definition != null) {
            response.setProcessDefinitionName(definition.getName());
        }

        // 流程编号
        Map<String, Object> processVariables = historicTask.getProcessVariables();
        if (processVariables != null && processVariables.get("processNo") != null) {
            response.setProcessNo(processVariables.get("processNo").toString());
        }

        response.setAssignee(historicTask.getAssignee());
        if (StrUtil.isNotBlank(historicTask.getAssignee())) {
            try {
                Long assigneeId = Long.parseLong(historicTask.getAssignee());
                response.setAssigneeName(userNameCache.getOrDefault(assigneeId, historicTask.getAssignee()));
            } catch (NumberFormatException e) {
                response.setAssigneeName(historicTask.getAssignee());
            }
        }

        response.setOwner(historicTask.getOwner());
        if (StrUtil.isNotBlank(historicTask.getOwner())) {
            try {
                Long ownerId = Long.parseLong(historicTask.getOwner());
                response.setOwnerName(userNameCache.getOrDefault(ownerId, historicTask.getOwner()));
            } catch (NumberFormatException e) {
                response.setOwnerName(historicTask.getOwner());
            }
        }

        if (historicTask.getCreateTime() != null) {
            response.setCreateTime(historicTask.getCreateTime());
        }
        if (historicTask.getClaimTime() != null) {
            response.setClaimTime(historicTask.getClaimTime());
        }
        if (historicTask.getDueDate() != null) {
            response.setDueDate(historicTask.getDueDate());
        }
        response.setCategory(historicTask.getCategory());

        // 设置结束时间（用于区分已办任务）
        if (historicTask.getEndTime() != null) {
            response.setEndTime(historicTask.getEndTime());
        }

        return response;
    }

    /**
     * 批量加载运行时任务关联的流程定义
     */
    private Map<String, ProcessDefinition> batchLoadProcessDefinitions(List<Task> tasks) {
        Set<String> processInstanceIds = tasks.stream()
                .map(Task::getProcessInstanceId)
                .collect(Collectors.toSet());

        Map<String, String> instanceToDefId = new HashMap<>();
        runtimeService.createProcessInstanceQuery()
                .processInstanceIds(processInstanceIds)
                .list()
                .forEach(pi -> instanceToDefId.put(pi.getId(), pi.getProcessDefinitionId()));

        Map<String, ProcessDefinition> result = new HashMap<>();
        instanceToDefId.values().stream().distinct().forEach(defId -> {
            ProcessDefinition def = repositoryService.getProcessDefinition(defId);
            if (def != null) {
                instanceToDefId.forEach((instId, dId) -> {
                    if (dId.equals(defId)) result.put(instId, def);
                });
            }
        });
        return result;
    }

    /**
     * 批量加载历史任务关联的流程定义
     */
    private Map<String, ProcessDefinition> batchLoadHistoricProcessDefinitions(List<HistoricTaskInstance> tasks) {
        Set<String> processInstanceIds = tasks.stream()
                .map(HistoricTaskInstance::getProcessInstanceId)
                .collect(Collectors.toSet());

        Map<String, String> instanceToDefId = new HashMap<>();
        historyService.createHistoricProcessInstanceQuery()
                .processInstanceIds(processInstanceIds)
                .list()
                .forEach(pi -> instanceToDefId.put(pi.getId(), pi.getProcessDefinitionId()));

        Map<String, ProcessDefinition> result = new HashMap<>();
        instanceToDefId.values().stream().distinct().forEach(defId -> {
            ProcessDefinition def = repositoryService.getProcessDefinition(defId);
            if (def != null) {
                instanceToDefId.forEach((instId, dId) -> {
                    if (dId.equals(defId)) result.put(instId, def);
                });
            }
        });
        return result;
    }

    /**
     * 批量加载运行时任务中的用户名称
     */
    private Map<Long, String> batchLoadUserNames(List<Task> tasks) {
        Set<Long> userIds = new HashSet<>();
        for (Task task : tasks) {
            if (StrUtil.isNotBlank(task.getAssignee())) {
                try { userIds.add(Long.parseLong(task.getAssignee())); } catch (NumberFormatException ignored) {}
            }
            if (StrUtil.isNotBlank(task.getOwner())) {
                try { userIds.add(Long.parseLong(task.getOwner())); } catch (NumberFormatException ignored) {}
            }
            // 候选任务的候选人ID
            if (StrUtil.isBlank(task.getAssignee())) {
                try {
                    taskService.getIdentityLinksForTask(task.getId()).stream()
                            .filter(link -> "candidate".equals(link.getType()) && link.getUserId() != null)
                            .forEach(link -> {
                                try { userIds.add(Long.parseLong(link.getUserId())); } catch (NumberFormatException ignored) {}
                            });
                } catch (Exception ignored) {}
            }
        }
        return flowableIdentityService.getUserNames(userIds);
    }

    /**
     * 批量加载历史任务中的用户名称
     */
    private Map<Long, String> batchLoadHistoricUserNames(List<HistoricTaskInstance> tasks) {
        Set<Long> userIds = new HashSet<>();
        for (HistoricTaskInstance task : tasks) {
            if (StrUtil.isNotBlank(task.getAssignee())) {
                try { userIds.add(Long.parseLong(task.getAssignee())); } catch (NumberFormatException ignored) {}
            }
            if (StrUtil.isNotBlank(task.getOwner())) {
                try { userIds.add(Long.parseLong(task.getOwner())); } catch (NumberFormatException ignored) {}
            }
        }
        return flowableIdentityService.getUserNames(userIds);
    }

    /**
     * 保存审批意见
     */
    private void saveApprovalComment(Task task, Long userId, String userName,
                                      String actionType, String commentText) {
        WfApprovalComment comment = new WfApprovalComment();
        comment.setProcessInstanceId(task.getProcessInstanceId());
        comment.setTaskId(task.getId());
        comment.setTaskDefKey(task.getTaskDefinitionKey());
        comment.setTaskName(task.getName());
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setActionType(actionType);
        comment.setCommentText(commentText);
        comment.setCreateTime(LocalDateTime.now());
        wfApprovalCommentMapper.insert(comment);
    }

    /**
     * 格式化日期
     */
    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).format(DATE_FORMATTER);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signCreateTask(String taskId, TaskSignCreateRequest request) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentUsername = SecurityUtils.getCurrentUsername();

        for (String userId : request.getUserIds()) {
            // 通过 newTask + saveTask 创建子任务
            org.flowable.task.service.impl.persistence.entity.TaskEntity subTask =
                    (org.flowable.task.service.impl.persistence.entity.TaskEntity) taskService.newTask();
            subTask.setParentTaskId(taskId);
            subTask.setName(task.getName() + "（加签）");
            subTask.setAssignee(userId);
            subTask.setOwner(String.valueOf(currentUserId));
            subTask.setProcessInstanceId(task.getProcessInstanceId());
            subTask.setProcessDefinitionId(task.getProcessDefinitionId());
            subTask.setTaskDefinitionKey(task.getTaskDefinitionKey());
            taskService.saveTask(subTask);

            taskService.addComment(taskId, task.getProcessInstanceId(), "SIGN_" + request.getType().toUpperCase(),
                    "加签给用户[" + userId + "]，原因：" + request.getReason());
        }

        saveApprovalComment(task, currentUserId, currentUsername, ApprovalActionTypeEnum.SIGN_CREATE.getCode(),
                "加签操作，类型：" + request.getType() + "，原因：" + request.getReason());
        log.info("任务加签成功：taskId={}, type={}, userIds={}", taskId, request.getType(), request.getUserIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signDeleteTask(String taskId, TaskSignDeleteRequest request) {
        Task parentTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (parentTask == null) {
            throw new BusinessException(404, "任务不存在");
        }

        Task childTask = taskService.createTaskQuery().taskId(request.getChildTaskId()).singleResult();
        if (childTask == null) {
            throw new BusinessException(400, "子任务不存在");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentUsername = SecurityUtils.getCurrentUsername();

        taskService.deleteTask(request.getChildTaskId(), "减签：" + request.getReason());
        saveApprovalComment(parentTask, currentUserId, currentUsername, ApprovalActionTypeEnum.SIGN_DELETE.getCode(),
                "减签操作，移除子任务[" + request.getChildTaskId() + "]，原因：" + request.getReason());
        log.info("任务减签成功：taskId={}, childTaskId={}", taskId, request.getChildTaskId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyTask(String taskId, TaskCopyRequest request) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (processInstance == null) {
            throw new BusinessException(404, "流程实例不存在");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 获取流程编号
        String processNo = null;
        try {
            Object noVar = taskService.getVariable(taskId, "processNo");
            if (noVar != null) {
                processNo = noVar.toString();
            }
        } catch (Exception ignored) {}

        for (Long copyUserId : request.getCopyUserIds()) {
            WfProcessInstanceCopy copy = new WfProcessInstanceCopy();
            copy.setStartUserId(currentUserId);
            String processInstanceName = processInstance.getName();
            if (processInstanceName == null) {
                org.flowable.engine.repository.ProcessDefinition processDefinition =
                        repositoryService.createProcessDefinitionQuery()
                                .processDefinitionId(processInstance.getProcessDefinitionId())
                                .singleResult();
                if (processDefinition != null) {
                    processInstanceName = processDefinition.getName();
                }
            }
            copy.setProcessInstanceName(processInstanceName);
            copy.setProcessInstanceId(processInstance.getId());
            copy.setProcessDefinitionId(processInstance.getProcessDefinitionId());
            copy.setCategory(processInstance.getProcessDefinitionCategory());
            copy.setActivityId(task.getTaskDefinitionKey());
            copy.setActivityName(task.getName());
            copy.setTaskId(taskId);
            copy.setUserId(copyUserId);
            copy.setReason(request.getReason());
            copy.setProcessNo(processNo);
            copy.setCreateTime(LocalDateTime.now());
            copy.setCreateBy(currentUserId);
            wfProcessInstanceCopyMapper.insert(copy);
        }

        String currentUsername = SecurityUtils.getCurrentUsername();
        saveApprovalComment(task, currentUserId, currentUsername, ApprovalActionTypeEnum.COPY.getCode(),
                "抄送给用户[" + request.getCopyUserIds() + "]，原因：" + request.getReason());
        log.info("任务抄送成功：taskId={}, copyUserIds={}", taskId, request.getCopyUserIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawTask(String taskId) {
        HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        if (historicTask == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (historicTask.getEndTime() == null) {
            throw new BusinessException(400, "任务尚未完成，无法撤回");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!String.valueOf(currentUserId).equals(historicTask.getAssignee())) {
            throw new BusinessException(403, "只能撤回自己完成的任务");
        }

        String processInstanceId = historicTask.getProcessInstanceId();

        // 查找流程实例当前是否有运行中的任务
        List<Task> runningTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();

        if (runningTasks.isEmpty()) {
            throw new BusinessException(400, "流程已结束，无法撤回");
        }

        // 只能撤回最近完成的任务
        List<HistoricTaskInstance> recentCompleted = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .listPage(0, 1);
        if (recentCompleted.isEmpty() || !recentCompleted.get(0).getId().equals(taskId)) {
            throw new BusinessException(400, "只能撤回最近完成的任务");
        }

        // 同一节点不允许重复撤回（防止无限循环）
        LambdaQueryWrapper<WfApprovalComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfApprovalComment::getProcessInstanceId, processInstanceId)
                .eq(WfApprovalComment::getTaskDefKey, historicTask.getTaskDefinitionKey())
                .eq(WfApprovalComment::getActionType, ApprovalActionTypeEnum.WITHDRAW.getCode());
        if (wfApprovalCommentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(400, "该节点已撤回过，不能再次撤回");
        }

        // 使用 Flowable 的跳转功能将流程退回到原任务节点
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(runningTasks.get(0).getTaskDefinitionKey(),
                        historicTask.getTaskDefinitionKey())
                .changeState();

        String currentUsername = SecurityUtils.getCurrentNickname();
        Task newTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(historicTask.getTaskDefinitionKey())
                .singleResult();
        if (newTask != null) {
            taskService.setAssignee(newTask.getId(), String.valueOf(currentUserId));
            saveApprovalComment(newTask, currentUserId, currentUsername, ApprovalActionTypeEnum.WITHDRAW.getCode(), "撤回任务");
        }

        log.info("任务撤回成功：taskId={}, processInstanceId={}", taskId, processInstanceId);
    }

    @Override
    public List<Map<String, String>> getChildTasks(String parentTaskId) {
        // 使用 native query 查询子任务，因为 Flowable TaskQuery 没有 taskParentTaskId 方法
        List<Task> allTasks = taskService.createTaskQuery()
                .processInstanceId(
                        taskService.createTaskQuery().taskId(parentTaskId).singleResult() != null
                                ? taskService.createTaskQuery().taskId(parentTaskId).singleResult().getProcessInstanceId()
                                : "")
                .list();

        Set<Long> userIds = new HashSet<>();
        for (Task task : allTasks) {
            if (StrUtil.isNotBlank(task.getAssignee())) {
                try { userIds.add(Long.parseLong(task.getAssignee())); } catch (NumberFormatException ignored) {}
            }
        }
        Map<Long, String> userNames = flowableIdentityService.getUserNames(userIds);

        return allTasks.stream()
                .filter(t -> parentTaskId.equals(t.getParentTaskId()))
                .map(task -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put("id", task.getId());
                    map.put("name", task.getName());
                    map.put("assignee", task.getAssignee());
                    map.put("assigneeName", task.getAssignee() != null ?
                            userNames.getOrDefault(Long.parseLong(task.getAssignee()), task.getAssignee()) : "");
                    map.put("createTime", formatDate(task.getCreateTime()));
                    return map;
                }).collect(Collectors.toList());
    }
}
