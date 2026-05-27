package com.forge.admin.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.utils.SecurityUtils;
import com.forge.admin.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.admin.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.admin.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.admin.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.admin.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.admin.modules.workflow.entity.WfApprovalComment;
import com.forge.admin.modules.workflow.identity.FlowableIdentityService;
import com.forge.admin.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.admin.modules.workflow.service.WfProcessInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程实例管理服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessInstanceServiceImpl implements WfProcessInstanceService {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final IdentityService identityService;
    private final FlowableIdentityService flowableIdentityService;
    private final WfApprovalCommentMapper wfApprovalCommentMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<ProcessInstanceResponse> pageInstance(ProcessInstanceQueryRequest request) {
        return queryInstances(request, null);
    }

    @Override
    public Page<ProcessInstanceResponse> getMyInstances(ProcessInstanceQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }
        return queryInstances(request, currentUserId);
    }

    @Override
    public ProcessInstanceResponse getInstanceById(String processInstanceId) {
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance == null) {
            throw new BusinessException(404, "流程实例不存在");
        }

        return convertToResponseSingle(historicInstance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startProcess(ProcessStartRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        // 设置Flowable认证用户
        flowableIdentityService.setAuthenticatedUserId(currentUserId);

        try {
            // 准备流程变量，注入 initiator
            Map<String, Object> variables = request.getVariables() != null
                    ? new HashMap<>(request.getVariables()) : new HashMap<>();
            variables.put("initiator", String.valueOf(currentUserId));

            // 发起流程
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(
                    request.getProcessDefinitionId(),
                    request.getBusinessKey(),
                    variables
            );

            // 获取发起后的第一个用户任务，用于记录审批意见
            Task firstTask = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .singleResult();

            // 保存初始提交意见
            String userName = flowableIdentityService.getUserName(currentUserId);
            String taskDefKey = firstTask != null ? firstTask.getTaskDefinitionKey() : "start";
            String taskName = firstTask != null ? firstTask.getName() : "发起流程";

            saveApprovalComment(
                    processInstance.getId(),
                    firstTask != null ? firstTask.getId() : null,
                    taskDefKey,
                    taskName,
                    currentUserId,
                    userName,
                    "submit",
                    request.getComment()
            );

            log.info("流程发起成功：processDefinitionId={}, processInstanceId={}, startUser={}",
                    request.getProcessDefinitionId(), processInstance.getId(), currentUserId);
        } finally {
            flowableIdentityService.clearAuthenticatedUserId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelProcess(String processInstanceId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        // 验证流程实例存在
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            throw new BusinessException(404, "流程实例不存在或已结束");
        }

        // 验证是否是发起人
        String startUserId = processInstance.getStartUserId();
        if (!String.valueOf(currentUserId).equals(startUserId)) {
            throw new BusinessException(403, "只有流程发起人才能取消流程");
        }

        // 保存取消意见
        String userName = flowableIdentityService.getUserName(currentUserId);
        saveApprovalComment(
                processInstanceId,
                null,
                "cancel",
                "取消流程",
                currentUserId,
                userName,
                "cancel",
                "用户主动取消流程"
        );

        // 删除流程实例
        runtimeService.deleteProcessInstance(processInstanceId, "用户主动取消");

        log.info("流程实例已取消：processInstanceId={}, operator={}", processInstanceId, currentUserId);
    }

    @Override
    public InputStream getInstanceDiagram(String processInstanceId) {
        // 获取历史流程实例
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (historicInstance == null) {
            throw new BusinessException(404, "流程实例不存在");
        }

        String processDefinitionId = historicInstance.getProcessDefinitionId();

        // 获取高亮的活动节点ID（当前正在执行的节点）
        List<String> activeActivityIds = getActiveActivityIds(processInstanceId);

        // 获取高亮的连线ID（已执行的连线）
        List<String> highFlows = getHighLightedFlows(processInstanceId);

        // 获取BPMN模型并生成带高亮的流程图
        ProcessDiagramGenerator diagramGenerator = new DefaultProcessDiagramGenerator();
        return diagramGenerator.generateDiagram(
                repositoryService.getBpmnModel(processDefinitionId),
                "png",
                activeActivityIds,
                highFlows,
                "宋体",
                "宋体",
                "宋体",
                null,
                1.0,
                true
        );
    }

    @Override
    public List<ApprovalCommentResponse> getApprovalComments(String processInstanceId) {
        LambdaQueryWrapper<WfApprovalComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfApprovalComment::getProcessInstanceId, processInstanceId)
                .orderByAsc(WfApprovalComment::getCreateTime);

        List<WfApprovalComment> comments = wfApprovalCommentMapper.selectList(wrapper);
        return comments.stream()
                .map(this::convertToCommentResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        // 先尝试从运行中的流程实例获取变量
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance != null) {
            // 运行中的流程，从 RuntimeService 获取变量
            return runtimeService.getVariables(processInstanceId);
        }

        // 流程已结束，从历史记录获取变量
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .includeProcessVariables()
                .singleResult();

        if (historicInstance != null) {
            return historicInstance.getProcessVariables();
        }

        return new HashMap<>();
    }

    // ========== 私有方法 ==========

    /**
     * 查询流程实例（通用方法）
     */
    private Page<ProcessInstanceResponse> queryInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        // 根据状态选择查询方式
        String status = request.getStatus();
        boolean queryFinished = "finished".equals(status) || "terminated".equals(status);

        if (queryFinished) {
            return queryFinishedInstances(request, startUserId, status);
        } else if ("running".equals(status)) {
            return queryRunningInstances(request, startUserId);
        } else {
            // 未指定状态：合并查询运行中和已完成的
            return queryAllInstances(request, startUserId);
        }
    }

    /**
     * 查询运行中的流程实例
     */
    private Page<ProcessInstanceResponse> queryRunningInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        var query = historyService.createHistoricProcessInstanceQuery()
                .unfinished();

        buildQueryConditions(query, request, startUserId);

        long total = query.count();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        List<HistoricProcessInstance> instances = query.orderByProcessInstanceStartTime().desc()
                .listPage(offset, request.getPageSize());

        return buildPageResult(instances, total, request.getPageNum(), request.getPageSize());
    }

    /**
     * 查询已完成的流程实例
     */
    private Page<ProcessInstanceResponse> queryFinishedInstances(ProcessInstanceQueryRequest request,
                                                                  Long startUserId, String status) {
        var query = historyService.createHistoricProcessInstanceQuery()
                .finished();

        if ("terminated".equals(status)) {
            // 已终止 = 被删除的实例
            query = historyService.createHistoricProcessInstanceQuery()
                    .deleted();
        }

        buildQueryConditions(query, request, startUserId);

        long total = query.count();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        List<HistoricProcessInstance> instances = query.orderByProcessInstanceEndTime().desc()
                .listPage(offset, request.getPageSize());

        return buildPageResult(instances, total, request.getPageNum(), request.getPageSize());
    }

    /**
     * 查询所有流程实例（不区分状态）
     */
    private Page<ProcessInstanceResponse> queryAllInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        var query = historyService.createHistoricProcessInstanceQuery();

        buildQueryConditions(query, request, startUserId);

        long total = query.count();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        List<HistoricProcessInstance> instances = query.orderByProcessInstanceStartTime().desc()
                .listPage(offset, request.getPageSize());

        return buildPageResult(instances, total, request.getPageNum(), request.getPageSize());
    }

    /**
     * 构建查询条件
     */
    private void buildQueryConditions(org.flowable.engine.history.HistoricProcessInstanceQuery query,
                                       ProcessInstanceQueryRequest request, Long startUserId) {
        if (startUserId != null) {
            query.startedBy(String.valueOf(startUserId));
        }
        if (StrUtil.isNotBlank(request.getProcessDefinitionName())) {
            query.processDefinitionName(request.getProcessDefinitionName());
        }
        if (StrUtil.isNotBlank(request.getStartUserName())) {
            // Flowable不直接支持按发起人名称查询，忽略此条件
        }
        if (StrUtil.isNotBlank(request.getStartTimeBegin())) {
            query.startedAfter(parseDate(request.getStartTimeBegin()));
        }
        if (StrUtil.isNotBlank(request.getStartTimeEnd())) {
            query.startedBefore(parseDate(request.getStartTimeEnd()));
        }
    }

    /**
     * 构建分页结果（批量预加载关联数据，避免 N+1 查询）
     */
    private Page<ProcessInstanceResponse> buildPageResult(List<HistoricProcessInstance> instances,
                                                          long total, int pageNum, int pageSize) {
        if (instances.isEmpty()) {
            Page<ProcessInstanceResponse> resultPage = new Page<>();
            resultPage.setCurrent(pageNum);
            resultPage.setSize(pageSize);
            resultPage.setTotal(total);
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        // 批量加载流程定义
        Map<String, ProcessDefinition> definitionCache = new HashMap<>();
        instances.stream()
                .map(HistoricProcessInstance::getProcessDefinitionId)
                .distinct()
                .forEach(defId -> definitionCache.computeIfAbsent(defId, repositoryService::getProcessDefinition));

        // 批量加载用户名称
        Set<Long> userIds = new HashSet<>();
        for (HistoricProcessInstance inst : instances) {
            if (StrUtil.isNotBlank(inst.getStartUserId())) {
                try { userIds.add(Long.parseLong(inst.getStartUserId())); } catch (NumberFormatException ignored) {}
            }
        }
        Map<Long, String> userNameCache = flowableIdentityService.getUserNames(userIds);

        List<ProcessInstanceResponse> records = instances.stream()
                .map(inst -> convertToResponse(inst, definitionCache, userNameCache))
                .collect(Collectors.toList());

        Page<ProcessInstanceResponse> resultPage = new Page<>();
        resultPage.setCurrent(pageNum);
        resultPage.setSize(pageSize);
        resultPage.setTotal(total);
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 将历史流程实例转换为响应对象（使用缓存数据）
     */
    private ProcessInstanceResponse convertToResponse(HistoricProcessInstance historicInstance,
                                                       Map<String, ProcessDefinition> definitionCache,
                                                       Map<Long, String> userNameCache) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(historicInstance.getId());
        response.setProcessDefinitionId(historicInstance.getProcessDefinitionId());

        ProcessDefinition definition = definitionCache.get(historicInstance.getProcessDefinitionId());
        if (definition != null) {
            response.setProcessDefinitionName(definition.getName());
            response.setProcessDefinitionKey(definition.getKey());
        }

        response.setBusinessKey(historicInstance.getBusinessKey());

        if (historicInstance.getStartTime() != null) {
            response.setStartTime(historicInstance.getStartTime());
        }
        if (historicInstance.getEndTime() != null) {
            response.setEndTime(historicInstance.getEndTime());
        }
        response.setDurationInMillis(historicInstance.getDurationInMillis());

        response.setStartUserId(historicInstance.getStartUserId());
        if (StrUtil.isNotBlank(historicInstance.getStartUserId())) {
            try {
                Long userId = Long.parseLong(historicInstance.getStartUserId());
                response.setStartUserName(userNameCache.getOrDefault(userId, historicInstance.getStartUserId()));
            } catch (NumberFormatException e) {
                response.setStartUserName(historicInstance.getStartUserId());
            }
        }

        // 获取当前活动节点名称（仅运行中的实例）
        if (historicInstance.getEndTime() == null) {
            String currentActivityName = getCurrentActivityName(historicInstance.getId());
            response.setCurrentActivityName(currentActivityName);

            // 检查挂起状态
            ProcessInstance runtimeInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(historicInstance.getId())
                    .singleResult();
            if (runtimeInstance != null) {
                response.setSuspensionState(runtimeInstance.isSuspended() ? 2 : 1);
            }
        }

        response.setDeleteReason(historicInstance.getDeleteReason());

        return response;
    }

    /**
     * 获取当前活动节点名称
     */
    private String getCurrentActivityName(String processInstanceId) {
        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .list();

        if (executions.isEmpty()) {
            return null;
        }

        // 获取当前活动节点
        List<String> activityIds = new ArrayList<>();
        for (Execution execution : executions) {
            String activityId = execution.getActivityId();
            if (activityId != null) {
                activityIds.add(activityId);
            }
        }

        if (activityIds.isEmpty()) {
            return null;
        }

        // 从BPMN模型获取节点名称
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance == null) {
            return null;
        }

        org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        if (bpmnModel == null) {
            return null;
        }

        List<String> names = new ArrayList<>();
        for (String activityId : activityIds) {
            org.flowable.bpmn.model.FlowNode flowNode = (org.flowable.bpmn.model.FlowNode)
                    bpmnModel.getMainProcess().getFlowElement(activityId);
            if (flowNode != null && flowNode.getName() != null) {
                names.add(flowNode.getName());
            }
        }

        return String.join(", ", names);
    }

    /**
     * 获取当前活跃的活动节点ID列表
     */
    private List<String> getActiveActivityIds(String processInstanceId) {
        List<Execution> executions = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .list();

        List<String> activeActivityIds = new ArrayList<>();
        for (Execution execution : executions) {
            String activityId = execution.getActivityId();
            if (activityId != null) {
                activeActivityIds.add(activityId);
            }
        }

        // 如果没有运行中的节点，获取历史中最后完成的活动
        if (activeActivityIds.isEmpty()) {
            List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .activityType("userTask")
                    .orderByHistoricActivityInstanceEndTime().desc()
                    .listPage(0, 1);

            if (!historicActivities.isEmpty()) {
                activeActivityIds.add(historicActivities.get(0).getActivityId());
            }
        }

        return activeActivityIds;
    }

    /**
     * 获取高亮连线ID列表
     */
    private List<String> getHighLightedFlows(String processInstanceId) {
        List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        // 获取所有已执行的活动节点ID
        List<String> historicActivityIds = historicActivities.stream()
                .map(HistoricActivityInstance::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 获取BPMN模型中的连线，筛选出源节点和目标节点都在已执行列表中的连线
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicInstance == null) {
            return Collections.emptyList();
        }

        org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(historicInstance.getProcessDefinitionId());
        if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
            return Collections.emptyList();
        }

        List<String> highFlows = new ArrayList<>();
        Collection<org.flowable.bpmn.model.FlowElement> flowElements = bpmnModel.getMainProcess().getFlowElements();

        for (org.flowable.bpmn.model.FlowElement flowElement : flowElements) {
            if (flowElement instanceof org.flowable.bpmn.model.SequenceFlow sequenceFlow) {
                String sourceRef = sequenceFlow.getSourceRef();
                String targetRef = sequenceFlow.getTargetRef();
                if (historicActivityIds.contains(sourceRef) && historicActivityIds.contains(targetRef)) {
                    highFlows.add(sequenceFlow.getId());
                }
            }
        }

        return highFlows;
    }

    /**
     * 保存审批意见
     */
    private void saveApprovalComment(String processInstanceId, String taskId,
                                      String taskDefKey, String taskName,
                                      Long userId, String userName,
                                      String actionType, String commentText) {
        WfApprovalComment comment = new WfApprovalComment();
        comment.setProcessInstanceId(processInstanceId);
        comment.setTaskId(taskId);
        comment.setTaskDefKey(taskDefKey);
        comment.setTaskName(taskName);
        comment.setUserId(userId);
        comment.setUserName(userName);
        comment.setActionType(actionType);
        comment.setCommentText(commentText);
        comment.setCreateTime(LocalDateTime.now());
        wfApprovalCommentMapper.insert(comment);
    }

    /**
     * 转换审批意见为响应对象
     */
    private ApprovalCommentResponse convertToCommentResponse(WfApprovalComment comment) {
        ApprovalCommentResponse response = new ApprovalCommentResponse();
        response.setId(comment.getId());
        response.setProcessInstanceId(comment.getProcessInstanceId());
        response.setTaskId(comment.getTaskId());
        response.setTaskDefKey(comment.getTaskDefKey());
        response.setTaskName(comment.getTaskName());
        response.setUserId(comment.getUserId());
        response.setUserName(comment.getUserName());
        response.setActionType(comment.getActionType());
        response.setCommentText(comment.getCommentText());
        response.setAttachmentIds(comment.getAttachmentIds());
        if (comment.getCreateTime() != null) {
            response.setCreateTime(comment.getCreateTime());
        }
        return response;
    }

    /**
     * 单条实例查询转换（不需要批量优化）
     */
    private ProcessInstanceResponse convertToResponseSingle(HistoricProcessInstance historicInstance) {
        Map<String, ProcessDefinition> definitionCache = new HashMap<>();
        definitionCache.put(historicInstance.getProcessDefinitionId(),
                repositoryService.getProcessDefinition(historicInstance.getProcessDefinitionId()));

        Set<Long> userIds = new HashSet<>();
        if (StrUtil.isNotBlank(historicInstance.getStartUserId())) {
            try { userIds.add(Long.parseLong(historicInstance.getStartUserId())); } catch (NumberFormatException ignored) {}
        }
        Map<Long, String> userNameCache = flowableIdentityService.getUserNames(userIds);

        return convertToResponse(historicInstance, definitionCache, userNameCache);
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

    /**
     * 解析日期字符串为Date对象
     */
    private Date parseDate(String dateStr) {
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(dateStr, DATE_FORMATTER);
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            throw new BusinessException(400, "日期格式错误，正确格式：yyyy-MM-dd HH:mm:ss");
        }
    }

    @Override
    public ApprovalDetailResponse getApprovalDetail(String processInstanceId) {
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new BusinessException(404, "流程实例不存在");
        }

        ApprovalDetailResponse detail = new ApprovalDetailResponse();
        detail.setProcessInstanceId(processInstanceId);
        detail.setProcessInstanceName(instance.getName());
        detail.setProcessDefinitionId(instance.getProcessDefinitionId());
        detail.setCategory(instance.getProcessDefinitionCategory());
        detail.setStartTime(instance.getStartTime() != null ?
                instance.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
        detail.setEndTime(instance.getEndTime() != null ?
                instance.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
        detail.setStatus(instance.getEndTime() != null ? 2 : 1);

        if (StrUtil.isNotBlank(instance.getStartUserId())) {
            try {
                detail.setStartUserId(Long.parseLong(instance.getStartUserId()));
            } catch (NumberFormatException ignored) {}
        }

        // 获取发起人名称
        Set<Long> userIds = new HashSet<>();
        if (detail.getStartUserId() != null) {
            userIds.add(detail.getStartUserId());
        }

        // 获取 BPMN XML
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(instance.getProcessDefinitionId())
                .singleResult();
        if (pd != null) {
            try {
                InputStream bpmnStream = repositoryService.getResourceAsStream(
                        pd.getDeploymentId(), pd.getResourceName());
                if (bpmnStream != null) {
                    detail.setBpmnXml(new String(bpmnStream.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                log.warn("读取BPMN XML失败: {}", e.getMessage());
            }
        }

        // 构建审批节点时间线
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        // 获取所有审批人 ID
        for (HistoricActivityInstance activity : activities) {
            if (StrUtil.isNotBlank(activity.getAssignee())) {
                try { userIds.add(Long.parseLong(activity.getAssignee())); } catch (NumberFormatException ignored) {}
            }
        }
        Map<Long, String> userNames = flowableIdentityService.getUserNames(userIds);
        detail.setStartUserName(userNames.getOrDefault(detail.getStartUserId(), ""));

        // 按活动节点分组
        Map<String, ApprovalDetailResponse.ApprovalNode> nodeMap = new LinkedHashMap<>();
        for (HistoricActivityInstance activity : activities) {
            String activityId = activity.getActivityId();
            ApprovalDetailResponse.ApprovalNode node = nodeMap.get(activityId);
            if (node == null) {
                node = new ApprovalDetailResponse.ApprovalNode();
                node.setActivityId(activityId);
                node.setActivityName(activity.getActivityName());
                node.setActivityType(activity.getActivityType());
                node.setStartTime(activity.getStartTime() != null ?
                        activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
                node.setEndTime(activity.getEndTime() != null ?
                        activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
                node.setStatus(activity.getEndTime() != null ? 2 : 1);
                node.setTasks(new ArrayList<>());
                nodeMap.put(activityId, node);
            }

            // 如果有审批人，添加到任务的列表
            if (StrUtil.isNotBlank(activity.getAssignee())) {
                ApprovalDetailResponse.ApprovalTask task = new ApprovalDetailResponse.ApprovalTask();
                task.setTaskId(activity.getTaskId());
                try { task.setUserId(Long.parseLong(activity.getAssignee())); } catch (NumberFormatException ignored) {}
                task.setUserName(userNames.getOrDefault(task.getUserId(), ""));
                task.setStatus(activity.getEndTime() != null ? 2 : 1);
                task.setCreateTime(activity.getStartTime() != null ?
                        activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);
                task.setEndTime(activity.getEndTime() != null ?
                        activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);

                // 查找审批意见
                if (StrUtil.isNotBlank(activity.getTaskId())) {
                    LambdaQueryWrapper<WfApprovalComment> commentWrapper = new LambdaQueryWrapper<>();
                    commentWrapper.eq(WfApprovalComment::getTaskId, activity.getTaskId())
                            .orderByDesc(WfApprovalComment::getCreateTime)
                            .last("LIMIT 1");
                    WfApprovalComment comment = wfApprovalCommentMapper.selectOne(commentWrapper);
                    if (comment != null) {
                        task.setComment(comment.getCommentText());
                    }
                }

                node.getTasks().add(task);
            }
        }

        detail.setNodes(new ArrayList<>(nodeMap.values()));
        return detail;
    }
}
