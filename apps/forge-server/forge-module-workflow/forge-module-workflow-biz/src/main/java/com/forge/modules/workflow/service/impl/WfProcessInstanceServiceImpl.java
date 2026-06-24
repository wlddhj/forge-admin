package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.*;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.core.enums.NodeSetType;
import com.aizuda.bpm.engine.core.enums.TaskState;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.engine.model.DynamicAssignee;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.framework.diagram.FlowLongDiagramGenerator;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import com.forge.modules.workflow.service.ProcessNoGenerator;
import com.forge.modules.workflow.service.WfProcessInstanceCopyService;
import com.forge.modules.workflow.service.WfProcessInstanceService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程实例管理服务实现 - FlowLong 版本
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessInstanceServiceImpl implements WfProcessInstanceService {
    @Resource
    private FlowLongEngine flowLongEngine;
    private final RuntimeService runtimeService;
    private final ProcessService processService;
    private final QueryService queryService;
    private final TaskService taskService;
    private final FlwInstanceMapper instanceMapper;
    private final FlwHisInstanceMapper hisInstanceMapper;
    private final FlwHisTaskMapper hisTaskMapper;
    private final FlwTaskActorMapper taskActorMapper;
    private final FlowLongDiagramGenerator diagramGenerator;
    private final FlowLongIdentityService identityService;
    private final WfApprovalCommentMapper approvalCommentMapper;
    private final WfProcessExtMapper processExtMapper;
    private final ProcessNoGenerator processNoGenerator;
    private final WfProcessInstanceCopyService copyService;
    private final ObjectMapper objectMapper;

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
        Long id = parseInstanceId(processInstanceId);

        // 先尝试获取活动实例
        FlwInstance instance = queryService.getInstance(id);
        if (instance != null) {
            return convertToResponse(instance);
        }

        // 获取历史实例
        FlwHisInstance hisInstance = queryService.getHistInstance(id);
        if (hisInstance == null) {
            throw new BusinessException(404, "流程实例不存在");
        }

        return convertToHisResponse(hisInstance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startProcess(ProcessStartRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        FlowCreator flowCreator = createFlowCreator(currentUserId);

        // 获取流程定义
        Long processId = parseProcessId(request.getProcessDefinitionId());
        FlwProcess process = processService.getProcessById(processId);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        // 处理发起人自选审批人
        Map<String, String[]> startUserSelectActors = request.getStartUserSelectActors();
        Set<String> dynamicAssigneeNodeKeys = new HashSet<>();
        if (startUserSelectActors != null && !startUserSelectActors.isEmpty()) {
            // 构建动态分配处理人员数据
            Map<String, Object> dynamicAssigneeMap = new HashMap<>();
            for (Map.Entry<String, String[]> entry : startUserSelectActors.entrySet()) {
                String nodeKey = entry.getKey();
                String[] actorIds = entry.getValue();
                if (actorIds != null && actorIds.length > 0) {
                    dynamicAssigneeNodeKeys.add(nodeKey);
                    // 构建处理人列表
                    List<NodeAssignee> assigneeList = new ArrayList<>();
                    for (String actorId : actorIds) {
                        String actorName = identityService.getUserName(Long.parseLong(actorId));
                        assigneeList.add(NodeAssignee.builder().id(actorId).name(actorName).build());
                    }
                    // 创建动态分配对象，type=1 表示指定成员
                    DynamicAssignee dynamicAssignee = DynamicAssignee.of(1, assigneeList);
                    dynamicAssigneeMap.put(nodeKey, dynamicAssignee);
                }
            }
            // 传递动态分配处理人员数据
            FlowDataTransfer.dynamicAssignee(dynamicAssigneeMap);
        }

        try {
            // 准备流程变量
            Map<String, Object> variables = request.getVariables() != null
                    ? new HashMap<>(request.getVariables()) : new HashMap<>();
            variables.put("initiator", String.valueOf(currentUserId));
            variables.put("processNo", processNoGenerator.generateNo());

            // 自定义模型校验：跳过已通过 FlowDataTransfer 传递审批人的发起人自选节点
            flowLongEngine.startInstanceById(processId, flowCreator, variables, false, nodeModel -> {
                // 递归检查所有节点
                checkNodeModelWithDynamicAssignee(nodeModel, dynamicAssigneeNodeKeys);
            }, null).ifPresent(instance -> {

                // 获取发起后的第一个任务
                List<FlwTask> tasks = queryService.getTasksByInstanceId(instance.getId());
                FlwTask firstTask = tasks.isEmpty() ? null : tasks.get(0);

                // 保存初始提交意见
                String userName = identityService.getUserName(currentUserId);
                String taskDefKey = firstTask != null ? firstTask.getTaskKey() : "start";
                String taskName = firstTask != null ? firstTask.getTaskName() : "发起流程";

                saveApprovalComment(
                        instance.getId(),
                        firstTask != null ? firstTask.getId() : null,
                        taskDefKey,
                        taskName,
                        currentUserId,
                        userName,
                        ApprovalActionTypeEnum.SUBMIT.getCode(),
                        request.getComment()
                );

                log.info("流程发起成功：processId={}, instanceId={}, startUser={}",
                        processId, instance.getId(), currentUserId);
            });
        } finally {
            // 清理 FlowDataTransfer 数据，避免线程复用时数据残留
            FlowDataTransfer.remove();
        }
    }

    /**
     * 递归检查节点模型，跳过已通过 FlowDataTransfer 传递审批人的发起人自选节点
     */
    private void checkNodeModelWithDynamicAssignee(NodeModel rootNode, Set<String> dynamicAssigneeNodeKeys) {
        // 获取所有子孙节点（参考 ModelHelper.getRootNodeAllChildNodes）
        List<NodeModel> allNodes = getAllChildNodes(rootNode);

        Set<String> nodeKeys = new HashSet<>();
        for (NodeModel node : allNodes) {
            // 检查节点KEY重复
            if (!nodeKeys.add(node.getNodeKey())) {
                throw new BusinessException(400, "流程节点KEY重复：" + node.getNodeKey());
            }

            // 检查审批节点
            if (TaskType.approval.eq(node.getType()) && com.aizuda.bpm.engine.assist.ObjectUtils.isEmpty(node.getNodeAssigneeList())) {
                if (NodeSetType.specifyMembers.eq(node.getSetType())) {
                    throw new BusinessException(400, "审批节点「" + node.getNodeName() + "」未配置处理人员");
                }
                if (NodeSetType.role.eq(node.getSetType())) {
                    throw new BusinessException(400, "审批节点「" + node.getNodeName() + "」未选择角色");
                }
                if (NodeSetType.initiatorSelected.eq(node.getSetType())) {
                    // 发起人自选：检查是否已通过 FlowDataTransfer 传递审批人
                    if (!dynamicAssigneeNodeKeys.contains(node.getNodeKey())) {
                        throw new BusinessException(400, "发起人自选节点「" + node.getNodeName() + "」未选择审批人");
                    }
                }
            }

            // 检查抄送节点
            if (TaskType.cc.eq(node.getType()) && com.aizuda.bpm.engine.assist.ObjectUtils.isEmpty(node.getNodeAssigneeList())) {
                if (NodeSetType.specifyMembers.eq(node.getSetType()) && !Boolean.TRUE.equals(node.getAllowSelection())) {
                    throw new BusinessException(400, "抄送节点「" + node.getNodeName() + "」未配置处理人员");
                }
                if (NodeSetType.role.eq(node.getSetType())) {
                    throw new BusinessException(400, "抄送节点「" + node.getNodeName() + "」未选择角色");
                }
                if (NodeSetType.initiatorSelected.eq(node.getSetType())) {
                    // 抄送发起人自选：检查是否已通过 FlowDataTransfer 传递审批人
                    if (!dynamicAssigneeNodeKeys.contains(node.getNodeKey())) {
                        throw new BusinessException(400, "抄送发起人自选节点「" + node.getNodeName() + "」未选择审批人");
                    }
                }
            }

            // 其他校验（自动通过、自动拒绝、路由、子流程等）
            if (TaskType.autoPass.eq(node.getType()) || TaskType.autoReject.eq(node.getType())) {
                if (!com.aizuda.bpm.engine.model.ModelHelper.inConditionNode(node) || node.getChildNode() != null) {
                    throw new BusinessException(400, "自动通过/拒绝节点「" + node.getNodeName() + "」配置错误");
                }
            }
            if (node.routeNode() && com.aizuda.bpm.engine.assist.ObjectUtils.isEmpty(node.getRouteNodes())) {
                throw new BusinessException(400, "路由节点「" + node.getNodeName() + "」未配置路由分支");
            }
            if (node.callProcessNode() && com.aizuda.bpm.engine.assist.ObjectUtils.isEmpty(node.getCallProcess())) {
                throw new BusinessException(400, "子流程节点「" + node.getNodeName() + "」未选择子流程");
            }
        }
    }

    /**
     * 获取节点及其所有子孙节点（参考 ModelHelper.getRootNodeAllChildNodes）
     */
    private List<NodeModel> getAllChildNodes(NodeModel node) {
        List<NodeModel> nodes = new ArrayList<>();
        collectNodes(node, nodes);
        return nodes;
    }

    /**
     * 递归收集所有节点
     */
    private void collectNodes(NodeModel node, List<NodeModel> nodes) {
        if (node == null) {
            return;
        }
        nodes.add(node);

        // 处理条件分支
        if (node.conditionNode()) {
            List<com.aizuda.bpm.engine.model.ConditionNode> conditionNodes = node.getConditionNodes();
            if (conditionNodes != null) {
                for (com.aizuda.bpm.engine.model.ConditionNode cn : conditionNodes) {
                    collectNodes(cn.getChildNode(), nodes);
                }
            }
            collectNodes(node.getChildNode(), nodes);
        }
        // 处理并行分支
        else if (node.parallelNode()) {
            List<com.aizuda.bpm.engine.model.ConditionNode> parallelNodes = node.getParallelNodes();
            if (parallelNodes != null) {
                for (com.aizuda.bpm.engine.model.ConditionNode pn : parallelNodes) {
                    collectNodes(pn.getChildNode(), nodes);
                }
            }
            collectNodes(node.getChildNode(), nodes);
        }
        // 处理包容分支
        else if (node.inclusiveNode()) {
            List<com.aizuda.bpm.engine.model.ConditionNode> inclusiveNodes = node.getInclusiveNodes();
            if (inclusiveNodes != null) {
                for (com.aizuda.bpm.engine.model.ConditionNode in : inclusiveNodes) {
                    collectNodes(in.getChildNode(), nodes);
                }
            }
            collectNodes(node.getChildNode(), nodes);
        }
        // 普通节点
        else {
            collectNodes(node.getChildNode(), nodes);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelProcess(String processInstanceId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        Long id = parseInstanceId(processInstanceId);

        // 验证流程实例存在
        FlwInstance instance = queryService.getInstance(id);
        if (instance == null) {
            throw new BusinessException(404, "流程实例不存在或已结束");
        }

        // 验证是否是发起人
        String startUserId = instance.getCreateId();
        if (!String.valueOf(currentUserId).equals(startUserId)) {
            throw new BusinessException(403, "只有流程发起人才能取消流程");
        }

        // 保存取消意见
        String userName = identityService.getUserName(currentUserId);
        saveApprovalComment(
                id,
                null,
                "cancel",
                "取消流程",
                currentUserId,
                userName,
                ApprovalActionTypeEnum.CANCEL.getCode(),
                "用户主动取消流程"
        );

        // 终止流程实例
        FlowCreator flowCreator = createFlowCreator(currentUserId);
        runtimeService.terminate(id, flowCreator);

        // 自动抄送
        try {
            FlwProcess process = processService.getProcessById(instance.getProcessId());
            if (process != null) {
                copyService.autoCopyOnProcessEnd(String.valueOf(id), String.valueOf(process.getId()), "流程取消自动抄送");
            }
        } catch (Exception e) {
            log.warn("流程取消自动抄送异常：instanceId={}", id, e);
        }

        log.info("流程实例已取消：instanceId={}, operator={}", id, currentUserId);
    }

    @Override
    public InputStream getInstanceDiagram(String processInstanceId) {
        Long id = parseInstanceId(processInstanceId);

        // 获取流程实例
        FlwInstance instance = queryService.getInstance(id);
        FlwHisInstance hisInstance = null;
        if (instance == null) {
            hisInstance = queryService.getHistInstance(id);
            if (hisInstance == null) {
                throw new BusinessException(404, "流程实例不存在");
            }
        }

        Long processId = instance != null ? instance.getProcessId() : hisInstance.getProcessId();
        FlwProcess process = processService.getProcessById(processId);
        if (process == null || StrUtil.isBlank(process.getModelContent())) {
            throw new BusinessException(404, "流程模型不存在");
        }

        // 获取当前活动节点（用于高亮显示）
        Set<String> activeNodes = new HashSet<>();
        if (instance != null) {
            // 运行中的实例，获取当前节点
            activeNodes.add(instance.getCurrentNodeKey());
            // 获取所有活动任务的节点 Key
            List<FlwTask> tasks = queryService.getTasksByInstanceId(id);
            for (FlwTask task : tasks) {
                activeNodes.add(task.getTaskKey());
            }
        }

        // 优先使用扩展表中的 modelJson（更完整的流程定义）
        WfProcessExt processExt = getProcessExtByProcessId(processId);
        String modelContent = null;
        if (processExt != null && StrUtil.isNotBlank(processExt.getModelJson())) {
            modelContent = processExt.getModelJson();
        } else {
            modelContent = process.getModelContent();
        }

        // 使用 FlowLong 流程图生成器生成 SVG
        return diagramGenerator.generateDiagram(modelContent, activeNodes);
    }

    @Override
    public List<ApprovalCommentResponse> getApprovalComments(String processInstanceId) {
        Long id = parseInstanceId(processInstanceId);

        LambdaQueryWrapper<WfApprovalComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfApprovalComment::getProcessInstanceId, id)
                .orderByAsc(WfApprovalComment::getCreateTime);

        List<WfApprovalComment> comments = approvalCommentMapper.selectList(wrapper);
        return comments.stream()
                .map(this::convertToCommentResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        Long id = parseInstanceId(processInstanceId);

        // 先尝试从运行中的流程实例获取变量
        FlwInstance instance = queryService.getInstance(id);
        if (instance != null) {
            String variableJson = instance.getVariable();
            if (StrUtil.isNotBlank(variableJson)) {
                try {
                    return objectMapper.readValue(variableJson, Map.class);
                } catch (Exception e) {
                    log.warn("解析流程变量失败：instanceId={}", id, e);
                }
            }
            return new HashMap<>();
        }

        // 流程已结束，从历史记录获取变量
        FlwHisInstance hisInstance = queryService.getHistInstance(id);
        if (hisInstance != null) {
            String variableJson = hisInstance.getVariable();
            if (StrUtil.isNotBlank(variableJson)) {
                try {
                    return objectMapper.readValue(variableJson, Map.class);
                } catch (Exception e) {
                    log.warn("解析历史流程变量失败：instanceId={}", id, e);
                }
            }
        }

        return new HashMap<>();
    }

    @Override
    public ApprovalDetailResponse getApprovalDetail(String processInstanceId) {
        Long id = parseInstanceId(processInstanceId);

        // 获取流程实例
        FlwInstance instance = queryService.getInstance(id);
        FlwHisInstance hisInstance = null;
        if (instance == null) {
            hisInstance = queryService.getHistInstance(id);
            if (hisInstance == null) {
                throw new BusinessException(404, "流程实例不存在");
            }
        }

        Long processId = instance != null ? instance.getProcessId() : hisInstance.getProcessId();
        FlwProcess process = processService.getProcessById(processId);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        ApprovalDetailResponse detail = new ApprovalDetailResponse();
        detail.setProcessInstanceId(processInstanceId);
        detail.setProcessInstanceName(instance != null ? instance.getCurrentNodeName() : hisInstance.getCurrentNodeName());
        detail.setProcessDefinitionId(String.valueOf(processId));
        detail.setStartTime(toLocalDateTime(instance != null ? instance.getCreateTime() : hisInstance.getCreateTime()));
        detail.setEndTime(toLocalDateTime(hisInstance != null ? hisInstance.getEndTime() : null));
        detail.setStatus(instance != null ? 1 : 2);

        // 发起人信息
        String startUserIdStr = instance != null ? instance.getCreateId() : hisInstance.getCreateId();
        if (StrUtil.isNotBlank(startUserIdStr)) {
            try {
                Long startUserId = Long.parseLong(startUserIdStr);
                detail.setStartUserId(startUserId);
                detail.setStartUserName(identityService.getUserName(startUserId));
            } catch (NumberFormatException ignored) {}
        }

        // 流程模型 JSON（从扩展表获取）
        WfProcessExt processExt = getProcessExtByProcessId(processId);
        if (processExt != null && StrUtil.isNotBlank(processExt.getModelJson())) {
            detail.setModelJson(processExt.getModelJson());
        }

        // 获取任务历史
        List<FlwHisTask> hisTasks = getHisTasksByInstanceId(id);

        // 获取活动任务
        List<FlwTask> activeTasks = instance != null ? queryService.getTasksByInstanceId(id) : Collections.emptyList();

        // 构建审批节点时间线
        buildApprovalNodes(detail, hisTasks, activeTasks);

        return detail;
    }

    // ========== 私有方法 ==========

    private Long parseInstanceId(String processInstanceId) {
        try {
            return Long.parseLong(processInstanceId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "流程实例ID格式错误");
        }
    }

    private Long parseProcessId(String processDefinitionId) {
        try {
            return Long.parseLong(processDefinitionId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "流程定义ID格式错误");
        }
    }

    private FlowCreator createFlowCreator(Long userId) {
        return new FlowCreator(String.valueOf(userId), identityService.getUserName(userId));
    }

    /**
     * 查询流程实例（通用方法）
     */
    private Page<ProcessInstanceResponse> queryInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        // FlowLong 使用 MyBatis Plus 直接查询 flw_instance 和 flw_his_instance 表
        // 根据状态选择查询方式
        String status = request.getStatus();

        if ("finished".equals(status) || "terminated".equals(status)) {
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
        LambdaQueryWrapper<FlwInstance> wrapper = new LambdaQueryWrapper<>();

        // 发起人筛选
        if (startUserId != null) {
            wrapper.eq(FlwInstance::getCreateId, String.valueOf(startUserId));
        }

        // 流程定义ID筛选
        if (StrUtil.isNotBlank(request.getProcessDefinitionId())) {
            wrapper.eq(FlwInstance::getProcessId, Long.parseLong(request.getProcessDefinitionId()));
        }

        // 流程名称筛选
        if (StrUtil.isNotBlank(request.getProcessName())) {
            wrapper.like(FlwInstance::getCurrentNodeName, request.getProcessName());
        }

        wrapper.orderByDesc(FlwInstance::getCreateTime);

        Page<FlwInstance> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwInstance> instancePage = instanceMapper.selectPage(pageParam, wrapper);

        List<ProcessInstanceResponse> records = instancePage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        Page<ProcessInstanceResponse> resultPage = new Page<>();
        resultPage.setCurrent(instancePage.getCurrent());
        resultPage.setSize(instancePage.getSize());
        resultPage.setTotal(instancePage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 查询已完成的流程实例
     */
    private Page<ProcessInstanceResponse> queryFinishedInstances(ProcessInstanceQueryRequest request,
                                                                  Long startUserId, String status) {
        LambdaQueryWrapper<FlwHisInstance> wrapper = new LambdaQueryWrapper<>();

        // 发起人筛选
        if (startUserId != null) {
            wrapper.eq(FlwHisInstance::getCreateId, String.valueOf(startUserId));
        }

        // 流程定义ID筛选
        if (StrUtil.isNotBlank(request.getProcessDefinitionId())) {
            wrapper.eq(FlwHisInstance::getProcessId, Long.parseLong(request.getProcessDefinitionId()));
        }

        // 状态筛选
        if ("finished".equals(status)) {
            // 审批通过：instance_state = 2
            wrapper.eq(FlwHisInstance::getInstanceState, 2);
        } else if ("terminated".equals(status)) {
            // 审批拒绝(3) 或 强制终止(6)
            wrapper.in(FlwHisInstance::getInstanceState, 3, 6);
        }

        wrapper.orderByDesc(FlwHisInstance::getCreateTime);

        Page<FlwHisInstance> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwHisInstance> hisPage = hisInstanceMapper.selectPage(pageParam, wrapper);

        List<ProcessInstanceResponse> records = hisPage.getRecords().stream()
                .map(this::convertToHisResponse)
                .collect(Collectors.toList());

        Page<ProcessInstanceResponse> resultPage = new Page<>();
        resultPage.setCurrent(hisPage.getCurrent());
        resultPage.setSize(hisPage.getSize());
        resultPage.setTotal(hisPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 查询所有流程实例（不区分状态）
     * 由于 FlowLong 分开存储活动实例和历史实例，需要分别查询后合并
     */
    private Page<ProcessInstanceResponse> queryAllInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        // 先查询运行中的实例
        ProcessInstanceQueryRequest runningRequest = new ProcessInstanceQueryRequest();
        runningRequest.setPageNum(request.getPageNum());
        runningRequest.setPageSize(request.getPageSize());
        runningRequest.setProcessDefinitionId(request.getProcessDefinitionId());
        runningRequest.setProcessName(request.getProcessName());

        Page<ProcessInstanceResponse> runningPage = queryRunningInstances(runningRequest, startUserId);

        // 如果运行中的实例数量不足一页，补充历史实例
        if (runningPage.getRecords().size() < request.getPageSize()) {
            int remaining = (int) (request.getPageSize() - runningPage.getRecords().size());
            ProcessInstanceQueryRequest finishedRequest = new ProcessInstanceQueryRequest();
            finishedRequest.setPageNum(1);
            finishedRequest.setPageSize(remaining);
            finishedRequest.setProcessDefinitionId(request.getProcessDefinitionId());
            finishedRequest.setProcessName(request.getProcessName());

            Page<ProcessInstanceResponse> finishedPage = queryFinishedInstances(finishedRequest, startUserId, "finished");
            runningPage.getRecords().addAll(finishedPage.getRecords());
            runningPage.setTotal(runningPage.getTotal() + finishedPage.getTotal());
        }

        return runningPage;
    }

    /**
     * 保存审批意见
     */
    private void saveApprovalComment(Long processInstanceId, Long taskId,
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
        approvalCommentMapper.insert(comment);
    }

    /**
     * 获取流程扩展信息
     */
    private WfProcessExt getProcessExtByProcessId(Long processId) {
        LambdaQueryWrapper<WfProcessExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessExt::getProcessId, processId);
        return processExtMapper.selectOne(wrapper);
    }

    /**
     * 获取历史任务列表
     */
    private List<FlwHisTask> getHisTasksByInstanceId(Long instanceId) {
        LambdaQueryWrapper<FlwHisTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlwHisTask::getInstanceId, instanceId)
                .orderByAsc(FlwHisTask::getCreateTime);
        return hisTaskMapper.selectList(wrapper);
    }

    /**
     * 构建审批节点时间线
     */
    private void buildApprovalNodes(ApprovalDetailResponse detail, List<FlwHisTask> hisTasks, List<FlwTask> activeTasks) {
        // TODO: 实现审批节点时间线构建
        detail.setNodes(Collections.emptyList());
    }

    /**
     * 转换活动实例为响应对象
     */
    private ProcessInstanceResponse convertToResponse(FlwInstance instance) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(String.valueOf(instance.getId()));
        response.setProcessDefinitionId(String.valueOf(instance.getProcessId()));

        // 从流程定义获取名称
        FlwProcess process = processService.getProcessById(instance.getProcessId());
        if (process != null) {
            response.setProcessDefinitionName(process.getProcessName());
            response.setProcessDefinitionKey(process.getProcessKey());
        }

        response.setBusinessKey(instance.getBusinessKey());
        response.setStartTime(toLocalDateTime(instance.getCreateTime()));

        // 发起人信息
        if (StrUtil.isNotBlank(instance.getCreateId())) {
            response.setStartUserId(instance.getCreateId());
            try {
                Long userId = Long.parseLong(instance.getCreateId());
                response.setStartUserName(identityService.getUserName(userId));
            } catch (NumberFormatException e) {
                response.setStartUserName(instance.getCreateBy());
            }
        }

        // 当前节点信息
        response.setCurrentActivityName(instance.getCurrentNodeName());

        // 获取当前任务的受理人/候选人信息
        fillCurrentAssigneeInfo(response, instance.getId());

        // 流程编号
        String variableJson = instance.getVariable();
        if (StrUtil.isNotBlank(variableJson)) {
            try {
                Map<String, Object> variables = objectMapper.readValue(variableJson, Map.class);
                if (variables.get("processNo") != null) {
                    response.setProcessNo(variables.get("processNo").toString());
                }
            } catch (Exception ignored) {}
        }

        // 扩展信息
        WfProcessExt processExt = getProcessExtByProcessId(instance.getProcessId());
        if (processExt != null) {
            response.setCategoryId(processExt.getCategoryId());
            // TODO: 设置分类名称
        }

        return response;
    }

    /**
     * 填充当前任务的受理人/候选人信息
     */
    private void fillCurrentAssigneeInfo(ProcessInstanceResponse response, Long instanceId) {
        // 获取当前实例的所有活动任务
        List<FlwTask> tasks = queryService.getTasksByInstanceId(instanceId);
        if (tasks.isEmpty()) {
            return;
        }

        List<String> assigneeNames = new ArrayList<>();
        List<String> candidateNames = new ArrayList<>();

        for (FlwTask task : tasks) {
            // 查询任务的参与者
            LambdaQueryWrapper<FlwTaskActor> actorWrapper = new LambdaQueryWrapper<>();
            actorWrapper.eq(FlwTaskActor::getTaskId, task.getId());
            List<FlwTaskActor> taskActors = taskActorMapper.selectList(actorWrapper);

            for (FlwTaskActor actor : taskActors) {
                String actorName = actor.getActorName();
                if (StrUtil.isBlank(actorName)) {
                    // 如果 actorName 为空，尝试通过 ID 获取名称
                    if (ActorType.user.eq(actor.getActorType())) {
                        // 用户类型
                        actorName = identityService.getUserName(actor.getActorId());
                    } else if (ActorType.role.eq(actor.getActorType())) {
                        // 角色/组类型
                        actorName = identityService.getGroupName(Long.parseLong(actor.getActorId()));
                    }
                }

                if (ActorType.user.eq(actor.getActorType())) {
                    // 用户类型 - 直接作为受理人或候选人
                    assigneeNames.add(actorName);
                } else if (ActorType.role.eq(actor.getActorType())) {
                    // 角色/组类型 - 作为候选组
                    candidateNames.add(actorName);
                }
            }
        }

        response.setCurrentAssigneeNames(assigneeNames);
        response.setCurrentCandidateNames(candidateNames);
    }

    /**
     * 转换历史实例为响应对象
     */
    private ProcessInstanceResponse convertToHisResponse(FlwHisInstance hisInstance) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(String.valueOf(hisInstance.getId()));
        response.setProcessDefinitionId(String.valueOf(hisInstance.getProcessId()));

        // 从流程定义获取名称
        FlwProcess process = processService.getProcessById(hisInstance.getProcessId());
        if (process != null) {
            response.setProcessDefinitionName(process.getProcessName());
            response.setProcessDefinitionKey(process.getProcessKey());
        }

        response.setBusinessKey(hisInstance.getBusinessKey());
        response.setStartTime(toLocalDateTime(hisInstance.getCreateTime()));
        response.setEndTime(toLocalDateTime(hisInstance.getEndTime()));
        response.setDurationInMillis(hisInstance.getDuration() != null ? hisInstance.getDuration().longValue() : null);

        // 发起人信息
        if (StrUtil.isNotBlank(hisInstance.getCreateId())) {
            response.setStartUserId(hisInstance.getCreateId());
            try {
                Long userId = Long.parseLong(hisInstance.getCreateId());
                response.setStartUserName(identityService.getUserName(userId));
            } catch (NumberFormatException e) {
                response.setStartUserName(hisInstance.getCreateBy());
            }
        }

        // 流程编号
        String variableJson = hisInstance.getVariable();
        if (StrUtil.isNotBlank(variableJson)) {
            try {
                Map<String, Object> variables = objectMapper.readValue(variableJson, Map.class);
                if (variables.get("processNo") != null) {
                    response.setProcessNo(variables.get("processNo").toString());
                }
            } catch (Exception ignored) {}
        }

        // 状态判断
        Integer instanceState = hisInstance.getInstanceState();
        if (instanceState != null) {
            // 2=审批通过, 3=审批拒绝, 5=超时结束, 6=强制终止
            response.setDeleteReason(instanceState == 3 ? "审批拒绝" : instanceState == 6 ? "强制终止" : null);
        }

        return response;
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
     * 将 java.util.Date 转换为 LocalDateTime
     */
    private LocalDateTime toLocalDateTime(java.util.Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}