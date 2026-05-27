package com.forge.admin.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.utils.SecurityUtils;
import com.forge.admin.modules.workflow.dto.task.*;
import com.forge.admin.modules.workflow.entity.WfApprovalComment;
import com.forge.admin.modules.workflow.identity.FlowableIdentityService;
import com.forge.admin.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.admin.modules.workflow.service.WfTaskService;
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<TaskResponse> getTodoTasks(TaskQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        var query = taskService.createTaskQuery()
                .taskAssignee(String.valueOf(currentUserId))
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

        var query = historyService.createHistoricTaskInstanceQuery()
                .finished()
                .taskAssignee(String.valueOf(currentUserId))
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
                .singleResult();

        if (task != null) {
            Map<String, ProcessDefinition> defCache = batchLoadProcessDefinitions(List.of(task));
            Map<Long, String> userCache = batchLoadUserNames(List.of(task));
            return convertTaskToResponse(task, defCache, userCache);
        }

        // 如果运行时任务不存在，尝试从历史记录获取
        HistoricTaskInstance historicTask = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId)
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
        saveApprovalComment(task, currentUserId, userName, "submit", request.getComment());

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
        saveApprovalComment(task, currentUserId, userName, "approve", request.getComment());

        log.info("任务审批通过：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(String taskId, TaskCompleteRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        // 设置审批驳回变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", false);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }

        taskService.complete(taskId, variables);

        // 保存审批驳回意见
        saveApprovalComment(task, currentUserId, userName, "reject", request.getComment());

        log.info("任务审批驳回：taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(String taskId, TaskDelegateRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = flowableIdentityService.getUserName(currentUserId);

        Task task = validateTask(taskId);
        validateTaskAssignee(task, currentUserId);

        String delegateUserId = String.valueOf(request.getDelegateUserId());

        // 委派任务
        taskService.delegateTask(taskId, delegateUserId);

        // 保存委派意见
        String delegateUserName = flowableIdentityService.getUserName(request.getDelegateUserId());
        String commentText = StrUtil.isNotBlank(request.getComment())
                ? request.getComment()
                : "任务委派给：" + delegateUserName;
        saveApprovalComment(task, currentUserId, userName, "delegate", commentText);

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
        saveApprovalComment(task, currentUserId, userName, "transfer", commentText);

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
        saveApprovalComment(task, currentUserId, userName, "return", request.getComment());

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
        if (task.getAssignee() == null || !task.getAssignee().equals(String.valueOf(currentUserId))) {
            throw new BusinessException(403, "当前用户不是该任务的处理人");
        }
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

        response.setAssignee(task.getAssignee());
        if (StrUtil.isNotBlank(task.getAssignee())) {
            try {
                Long assigneeId = Long.parseLong(task.getAssignee());
                response.setAssigneeName(userNameCache.getOrDefault(assigneeId, task.getAssignee()));
            } catch (NumberFormatException e) {
                response.setAssigneeName(task.getAssignee());
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
}
