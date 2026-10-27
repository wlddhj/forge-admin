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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.common.utils.UserContext;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.modules.workflow.dto.comment.AttachmentBrief;
import com.forge.modules.workflow.dto.instance.AiDecisionResponse;
import com.forge.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.modules.workflow.entity.WfAiApprovalRecord;
import com.forge.modules.workflow.entity.WfAiRouteRecord;
import com.forge.modules.workflow.entity.WfApprovalComment;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.framework.diagram.FlowLongDiagramGenerator;
import com.forge.modules.workflow.framework.timeline.ApprovalTimelineBuilder;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper;
import com.forge.modules.workflow.mapper.WfAiApprovalRecordMapper;
import com.forge.modules.workflow.mapper.WfAiRouteRecordMapper;
import com.forge.modules.workflow.mapper.WfApprovalCommentMapper;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import com.forge.modules.workflow.service.ProcessNoGenerator;
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
    private final WfAiRouteRecordMapper aiRouteRecordMapper;
    private final WfAiApprovalRecordMapper aiApprovalRecordMapper;
    private final com.forge.modules.workflow.mapper.WfInstanceQueryMapper instanceQueryMapper;
    private final SysAttachmentService sysAttachmentService;
    private final WfProcessExtMapper processExtMapper;
    private final ProcessNoGenerator processNoGenerator;
    private final ApprovalTimelineBuilder approvalTimelineBuilder;
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

        // 发起范围校验（0 全员 / 1 指定人员 / 2 均不可发起；工作流管理员豁免）
        checkStartScope(process, processId, currentUserId);

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
//            variables.put("processNo", processNoGenerator.generateNo());

            // 获取 businessKey 和 priority
            String businessKey = request.getBusinessKey();
            Integer priority = request.getPriority() != null ? request.getPriority() : 0;
            Integer urgent = request.getUrgent() != null ? request.getUrgent() : 0;

            // 自定义模型校验：跳过已通过 FlowDataTransfer 传递审批人的发起人自选节点
            // 使用 supplier 创建带有 businessKey 和 priority 的 FlwInstance
            flowLongEngine.startInstanceById(processId, flowCreator, variables, false, nodeModel -> {
                // 递归检查所有节点
                checkNodeModelWithDynamicAssignee(nodeModel, dynamicAssigneeNodeKeys);
            }, () -> {
                FlwInstance instance = FlwInstance.of(businessKey);
                instance.setPriority(priority);
                instance.setUrgent(urgent);
                // 设置流程编号
                instance.setInstanceNo(processNoGenerator.generateNo());
                return instance;
            }).ifPresent(instance -> {
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
        // 路由/驳回策略的跳转目标（nodeKey, 目标key），循环后统一校验存在性
        List<String[]> jumpTargets = new ArrayList<>();
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

            // AI 智能路由网关校验
            checkAiRoutingGateway(node);

            // 条件审批节点（type=3）：复用审批人校验 + 校验条件列表
            if (TaskType.conditionNode.eq(node.getType())) {
                // 审批人配置（与 type=1 一致）
                if (com.aizuda.bpm.engine.assist.ObjectUtils.isEmpty(node.getNodeAssigneeList())) {
                    if (NodeSetType.specifyMembers.eq(node.getSetType())) {
                        throw new BusinessException(400, "条件审批节点「" + node.getNodeName() + "」未配置处理人员");
                    }
                    if (NodeSetType.role.eq(node.getSetType())) {
                        throw new BusinessException(400, "条件审批节点「" + node.getNodeName() + "」未选择角色");
                    }
                }
                // 条件列表（从 extendConfig.conditionList 读取）
                Map<String, Object> extendConfig = node.getExtendConfig();
                Object condListObj = extendConfig == null ? null : extendConfig.get("conditionList");
                if (!(condListObj instanceof java.util.List) || ((java.util.List<?>) condListObj).isEmpty()) {
                    throw new BusinessException(400, "条件审批节点「" + node.getNodeName() + "」未配置条件列表");
                }
            }

            // 触发器任务节点（type=7）：按 triggerType 模式校验
            if (TaskType.trigger.eq(node.getType())) {
                Map<String, Object> extendConfig = node.getExtendConfig();
                String mode = extendConfig == null ? null
                        : (extendConfig.get("triggerType") == null ? null : extendConfig.get("triggerType").toString());

                if (mode == null || mode.isEmpty()) {
                    throw new BusinessException(400, "触发器任务节点「" + node.getNodeName() + "」未配置 triggerType（业务模式）");
                }

                switch (mode) {
                    case "expression":
                        requireField(extendConfig, "triggerExpression", node.getNodeName(), "expression 模式");
                        break;
                    case "bean":
                        requireField(extendConfig, "triggerBean", node.getNodeName(), "bean 模式");
                        requireField(extendConfig, "triggerMethod", node.getNodeName(), "bean 模式");
                        break;
                    case "class":
                        requireField(extendConfig, "triggerClass", node.getNodeName(), "class 模式");
                        requireField(extendConfig, "triggerMethod", node.getNodeName(), "class 模式");
                        break;
                    case "delegateExpression":
                        requireField(extendConfig, "triggerExpression", node.getNodeName(), "delegateExpression 模式");
                        requireField(extendConfig, "triggerMethod", node.getNodeName(), "delegateExpression 模式");
                        break;
                    default:
                        throw new BusinessException(400, "触发器任务节点「" + node.getNodeName()
                                + "」的 triggerType 非法: " + mode
                                + "（应为 expression / bean / class / delegateExpression）");
                }

                if (node.getChildNode() == null) {
                    throw new BusinessException(400, "触发器任务节点「" + node.getNodeName() + "」未配置 childNode（执行后无下游节点）");
                }
            }

            // ===== 迭代二新增节点校验 =====

            // 并行分支：至少 2 个分支
            if (node.parallelNode()) {
                List<com.aizuda.bpm.engine.model.ConditionNode> parallelNodes = node.getParallelNodes();
                if (parallelNodes == null || parallelNodes.size() < 2) {
                    throw new BusinessException(400, "并行分支「" + node.getNodeName() + "」至少需要 2 个分支");
                }
            }

            // 包容分支：至少 1 条无条件兜底分支（引擎 defaultConditionNode 取第一个空条件分支，缺失时全不满足会异常）
            if (node.inclusiveNode()) {
                List<com.aizuda.bpm.engine.model.ConditionNode> inclusiveNodes = node.getInclusiveNodes();
                if (inclusiveNodes == null || inclusiveNodes.isEmpty()) {
                    throw new BusinessException(400, "包容分支「" + node.getNodeName() + "」未配置分支");
                }
                boolean hasDefault = inclusiveNodes.stream()
                        .anyMatch(cn -> cn.getConditionList() == null || cn.getConditionList().isEmpty());
                if (!hasDefault) {
                    throw new BusinessException(400, "包容分支「" + node.getNodeName() + "」缺少无条件兜底分支（请保留一条不设条件的分支）");
                }
            }

            // 路由分支：nodeKey 必须为 route:目标 格式；容器有后续节点时各分支必须设条件（引擎约束）
            if (node.routeNode()) {
                for (com.aizuda.bpm.engine.model.ConditionNode rn : node.getRouteNodes()) {
                    if (rn.getNodeKey() == null || !rn.getNodeKey().startsWith("route:")) {
                        throw new BusinessException(400, "路由分支「" + rn.getNodeName() + "」未设置跳转目标节点");
                    }
                    if (node.getChildNode() != null
                            && (rn.getConditionList() == null || rn.getConditionList().isEmpty())) {
                        throw new BusinessException(400, "路由分支「" + rn.getNodeName()
                                + "」未设置条件（路由节点存在后续节点时各路由分支必须设置条件）");
                    }
                    jumpTargets.add(new String[]{node.getNodeName(), rn.getNodeKey().substring("route:".length())});
                }
            }

            // 子流程：callProcess 格式 "id:name" 且目标流程存在
            if (node.callProcessNode()) {
                String callProcess = node.getCallProcess();
                String processIdPart = callProcess.contains(":") ? callProcess.substring(0, callProcess.indexOf(':')) : callProcess;
                FlwProcess called;
                try {
                    called = processService.getProcessById(Long.parseLong(processIdPart));
                } catch (NumberFormatException e) {
                    called = null;
                }
                if (called == null) {
                    throw new BusinessException(400, "子流程节点「" + node.getNodeName() + "」选择的子流程不存在（可能已删除或停用）");
                }
            }

            // 定时器：extendConfig.time 格式（数值:单位 或 HH:mm:ss）
            if (TaskType.timer.eq(node.getType())) {
                Map<String, Object> extendConfig = node.getExtendConfig();
                Object time = extendConfig == null ? null : extendConfig.get("time");
                if (time == null || !(time.toString().matches("\\d+:[dhm]") || time.toString().matches("\\d{2}:\\d{2}:\\d{2}"))) {
                    throw new BusinessException(400, "定时器节点「" + node.getNodeName() + "」等待时间配置非法（应为 数值:d/h/m 或 HH:mm:ss）");
                }
            }

            // 票签：权重配置完整性（指定成员时若有任一人配置权重则全部需配置且合计>0）
            if ((TaskType.approval.eq(node.getType()) || TaskType.conditionNode.eq(node.getType()))
                    && Objects.equals(4, node.getExamineMode())
                    && NodeSetType.specifyMembers.eq(node.getSetType())
                    && node.getNodeAssigneeList() != null && !node.getNodeAssigneeList().isEmpty()) {
                boolean anyWeight = node.getNodeAssigneeList().stream().anyMatch(a -> a.getWeight() != null);
                if (anyWeight) {
                    boolean allWeight = node.getNodeAssigneeList().stream().allMatch(a -> a.getWeight() != null && a.getWeight() > 0);
                    int total = node.getNodeAssigneeList().stream().mapToInt(a -> a.getWeight() == null ? 0 : a.getWeight()).sum();
                    if (!allWeight || total <= 0) {
                        throw new BusinessException(400, "票签节点「" + node.getNodeName() + "」权重需全部配置且合计大于 0");
                    }
                }
            }

            // 驳回策略：取值合法；策略 3 需配置目标节点
            if (node.getRejectStrategy() != null) {
                Integer strategy = node.getRejectStrategy();
                if (strategy < 1 || strategy > 3) {
                    throw new BusinessException(400, "审批节点「" + node.getNodeName() + "」驳回策略取值非法：" + strategy);
                }
                if (strategy == 3) {
                    Map<String, Object> extendConfig = node.getExtendConfig();
                    Object target = extendConfig == null ? null : extendConfig.get("rejectTargetKey");
                    if (target == null || target.toString().isEmpty()) {
                        throw new BusinessException(400, "审批节点「" + node.getNodeName() + "」驳回策略为指定节点时未选择目标节点");
                    }
                    jumpTargets.add(new String[]{node.getNodeName(), target.toString()});
                }
            }
        }

        // 跳转目标存在性统一校验（路由目标 / 驳回指定节点）
        for (String[] jump : jumpTargets) {
            if (!nodeKeys.contains(jump[1])) {
                throw new BusinessException(400, "节点「" + jump[0] + "」的跳转目标节点不存在：" + jump[1]);
            }
        }
    }

    /**
     * AI 智能路由网关校验：仅分支节点可启用；启用后必须保留无条件默认分支作为降级路径
     * （AI 失败/超时/低置信度时引擎落回 SpEL 条件，均不中而无默认分支会抛 Not found executable ConditionNode）
     */
    void checkAiRoutingGateway(NodeModel node) {
        if (node.getCallAi() == null) {
            return;
        }
        if (!node.conditionNode() && !node.inclusiveNode() && !node.routeNode()) {
            throw new BusinessException(400, "节点「" + node.getNodeName() + "」配置了AI路由，但仅分支节点支持AI路由");
        }
        List<com.aizuda.bpm.engine.model.ConditionNode> aiBranches = node.routeNode() ? node.getRouteNodes()
                : node.inclusiveNode() ? node.getInclusiveNodes() : node.getConditionNodes();
        boolean hasDefaultBranch = aiBranches != null && aiBranches.stream()
                .anyMatch(b -> com.aizuda.bpm.engine.assist.ObjectUtils.isEmpty(b.getConditionList()));
        if (!hasDefaultBranch) {
            throw new BusinessException(400, "AI路由网关「" + node.getNodeName() + "」必须保留一个无条件默认分支作为降级路径");
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
        // 处理路由分支（路由分支子链不执行，但参与节点收集以便 KEY 查重与目标校验）
        else if (node.routeNode()) {
            List<com.aizuda.bpm.engine.model.ConditionNode> routeNodes = node.getRouteNodes();
            if (routeNodes != null) {
                for (com.aizuda.bpm.engine.model.ConditionNode rn : routeNodes) {
                    collectNodes(rn.getChildNode(), nodes);
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

        // 终止流程实例（自动抄送统一由 WfAutoCopyEventHandler 在终态事件提交后处理）
        FlowCreator flowCreator = createFlowCreator(currentUserId);
        runtimeService.terminate(id, flowCreator);

        log.info("流程实例已取消：instanceId={}, operator={}", id, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeInstance(String processInstanceId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }
        Long id = parseInstanceId(processInstanceId);

        if (queryService.getInstance(id) != null) {
            throw new BusinessException(400, "流程运行中，无需唤醒");
        }
        FlwHisInstance hisInstance = queryService.getHistInstance(id);
        if (hisInstance == null) {
            throw new BusinessException(404, "流程实例不存在");
        }

        // 仅可唤醒被驳回/撤销/超时/终止/自动拒绝结束的实例（通过结束的流程唤醒无意义）
        Integer state = hisInstance.getInstanceState();
        boolean resumable = InstanceState.reject.eq(state) || InstanceState.revoke.eq(state)
                || InstanceState.timeout.eq(state) || InstanceState.terminate.eq(state)
                || InstanceState.autoReject.eq(state);
        if (!resumable) {
            throw new BusinessException(400, "当前实例状态不可唤醒");
        }

        // 仅发起人或工作流管理员可唤醒
        if (!String.valueOf(currentUserId).equals(hisInstance.getCreateId()) && !hasWorkflowAdminRole()) {
            throw new BusinessException(403, "只有流程发起人或工作流管理员才能唤醒");
        }

        FlowCreator flowCreator = createFlowCreator(currentUserId);
        flowLongEngine.executeResumeTask(id, flowCreator, null);

        saveApprovalComment(id, null, "resume", "唤醒", currentUserId,
                identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.RESUME.getCode(), "唤醒流程实例，从上次中断节点继续");
        log.info("流程实例已唤醒：instanceId={}, operator={}", id, currentUserId);
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

    /**
     * 发起范围校验（0 全员 / 1 指定人员 / 2 均不可发起；工作流管理员豁免）
     */
    void checkStartScope(FlwProcess process, Long processId, Long currentUserId) {
        if (process.getUseScope() == null || process.getUseScope() <= 0 || hasWorkflowAdminRole()) {
            return;
        }
        if (process.getUseScope() == 2) {
            throw new BusinessException(403, "该流程已设置为不可发起，请联系管理员");
        }
        WfProcessExt processExt = getProcessExtByProcessId(processId);
        boolean allowed = false;
        if (processExt != null && StrUtil.isNotBlank(processExt.getUseScopeUsers())) {
            try {
                // TypeReference 统一反序列化为 Long——裸 List.class 会得到 Integer，
                // 与 currentUserId(Long) 的 contains 比较恒为 false 导致名单内用户也被拒
                List<Long> scopeUsers = objectMapper.readValue(processExt.getUseScopeUsers(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
                allowed = scopeUsers != null && scopeUsers.contains(currentUserId);
            } catch (Exception e) {
                log.warn("解析发起范围名单失败，按无权限处理: processId={}", processId, e);
            }
        }
        if (!allowed) {
            throw new BusinessException(403, "您没有该流程的发起权限");
        }
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
        List<ApprovalCommentResponse> responses = comments.stream()
                .map(this::convertToCommentResponse)
                .collect(Collectors.toList());
        fillCommentAttachments(comments, responses);
        return responses;
    }

    @Override
    public AiDecisionResponse getAiDecisions(String processInstanceId) {
        Long id = parseInstanceId(processInstanceId);
        AiDecisionResponse response = new AiDecisionResponse();
        response.setRoutings(loadRoutingDecisions(id));
        response.setApprovals(loadApprovalDecisions(id));
        return response;
    }

    /**
     * AI 路由决策记录：选中分支 nodeKey 解析为分支名（candidate_branches JSON）
     */
    private List<AiDecisionResponse.Routing> loadRoutingDecisions(Long instanceId) {
        List<WfAiRouteRecord> records;
        try {
            records = aiRouteRecordMapper.selectList(new LambdaQueryWrapper<WfAiRouteRecord>()
                    .eq(WfAiRouteRecord::getProcessInstanceId, instanceId)
                    .orderByDesc(WfAiRouteRecord::getId));
        } catch (Exception e) {
            // 表缺失等环境差异不阻断接口（如 H2 测试库未建该表）
            log.warn("查询AI路由决策记录失败: instanceId={}, error={}", instanceId, e.getMessage());
            return Collections.emptyList();
        }
        return records.stream().map(record -> {
            AiDecisionResponse.Routing dto = new AiDecisionResponse.Routing();
            dto.setGatewayNodeKey(record.getGatewayNodeKey());
            dto.setGatewayNodeName(record.getGatewayNodeName());
            dto.setGatewayType(record.getGatewayType());
            dto.setGatewayTypeName(gatewayTypeName(record.getGatewayType()));
            dto.setConfidence(record.getConfidence());
            dto.setReasoning(record.getReasoning());
            dto.setRawResponse(record.getRawResponse());
            dto.setStatus(record.getStatus());
            dto.setCreateTime(record.getCreateTime());

            List<String> selectedKeys = StrUtil.isBlank(record.getSelectedBranches())
                    ? Collections.emptyList()
                    : Arrays.asList(record.getSelectedBranches().split(","));
            dto.setSelectedBranches(selectedKeys);
            Map<String, String> branchNames = parseCandidateBranchNames(record.getCandidateBranches());
            dto.setSelectedBranchNames(selectedKeys.stream()
                    .map(key -> branchNames.getOrDefault(key, key))
                    .collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * AI 审批决策记录（独立 try-catch：单组失败不影响另一组）
     */
    private List<AiDecisionResponse.Approval> loadApprovalDecisions(Long instanceId) {
        List<WfAiApprovalRecord> records;
        try {
            records = aiApprovalRecordMapper.selectList(new LambdaQueryWrapper<WfAiApprovalRecord>()
                    .eq(WfAiApprovalRecord::getProcessInstanceId, instanceId)
                    .orderByDesc(WfAiApprovalRecord::getId));
        } catch (Exception e) {
            log.warn("查询AI审批决策记录失败: instanceId={}, error={}", instanceId, e.getMessage());
            return Collections.emptyList();
        }
        return records.stream().map(record -> {
            AiDecisionResponse.Approval dto = new AiDecisionResponse.Approval();
            dto.setTaskId(record.getTaskId());
            dto.setTaskName(record.getTaskName());
            dto.setDecision(record.getDecision());
            dto.setConfidence(record.getConfidence());
            dto.setReasoning(record.getReasoning());
            dto.setStatus(record.getStatus());
            dto.setProvider(record.getProvider());
            dto.setModelName(record.getModelName());
            dto.setCreateTime(record.getCreateTime());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 解析候选分支 JSON（[{nodeKey,nodeName}]）为 nodeKey → nodeName 映射；解析失败返回空映射（调用方回退展示原 key）
     */
    private Map<String, String> parseCandidateBranchNames(String candidateBranches) {
        if (StrUtil.isBlank(candidateBranches)) {
            return Collections.emptyMap();
        }
        try {
            List<Map<String, String>> candidates = objectMapper.readValue(candidateBranches,
                    new TypeReference<List<Map<String, String>>>() {
                    });
            return candidates.stream().collect(Collectors.toMap(
                    c -> c.getOrDefault("nodeKey", ""),
                    c -> c.getOrDefault("nodeName", ""),
                    (a, b) -> a));
        } catch (Exception e) {
            log.warn("解析候选分支JSON失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String gatewayTypeName(Integer gatewayType) {
        if (gatewayType == null) {
            return "未知";
        }
        return switch (gatewayType) {
            case 4 -> "条件网关";
            case 9 -> "包容网关";
            case 23 -> "路由网关";
            default -> "未知";
        };
    }

    /**
     * 按评论的 attachment_ids 批量填充附件明细（一次查询，避免逐条 N+1）
     */
    private void fillCommentAttachments(List<WfApprovalComment> comments, List<ApprovalCommentResponse> responses) {
        List<Long> allAttachmentIds = comments.stream()
                .map(WfApprovalComment::getAttachmentIds)
                .filter(StrUtil::isNotBlank)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .filter(s -> s.matches("\\d+"))
                .map(Long::parseLong)
                .distinct()
                .collect(Collectors.toList());
        if (allAttachmentIds.isEmpty()) {
            return;
        }

        Map<Long, SysAttachment> attachmentMap = sysAttachmentService.listByIds(allAttachmentIds).stream()
                .collect(Collectors.toMap(SysAttachment::getId, a -> a));
        for (int i = 0; i < comments.size(); i++) {
            String idsStr = comments.get(i).getAttachmentIds();
            if (StrUtil.isBlank(idsStr)) {
                continue;
            }
            List<AttachmentBrief> briefs = Arrays.stream(idsStr.split(","))
                    .filter(s -> s.matches("\\d+"))
                    .map(Long::parseLong)
                    .map(attachmentMap::get)
                    .filter(Objects::nonNull)
                    .map(attachment -> {
                        AttachmentBrief brief = new AttachmentBrief();
                        brief.setId(attachment.getId());
                        brief.setOriginalName(attachment.getOriginalName());
                        brief.setFileUrl(attachment.getFileUrl());
                        return brief;
                    })
                    .collect(Collectors.toList());
            responses.get(i).setAttachments(briefs);
        }
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
        buildApprovalNodes(detail, id, processId, hisTasks, activeTasks);

        return detail;
    }

    // ========== 私有方法 ==========

    /**
     * 构造流程名称模糊查询字符串，兼容 processName / processDefinitionName 两个入参字段。
     * 返回 null 表示无名称筛选条件。
     */
    private String buildProcessNameLike(ProcessInstanceQueryRequest request) {
        String name = StrUtil.blankToDefault(request.getProcessName(), request.getProcessDefinitionName());
        if (StrUtil.isBlank(name)) {
            return null;
        }
        return "%" + name.trim() + "%";
    }

    private void requireField(Map<String, Object> extendConfig, String field, String nodeName, String mode) {
        Object value = extendConfig == null ? null : extendConfig.get(field);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new BusinessException(400,
                    "触发器任务节点「" + nodeName + "」(" + mode + ") 未配置 " + field);
        }
    }

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
        return FlowCreatorFactory.user(userId, identityService.getUserName(userId));
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

        // 流程名称筛选（模糊匹配 flw_process.process_name，兼容 processName / processDefinitionName 两个字段）
        String processNameLike = buildProcessNameLike(request);
        if (processNameLike != null) {
            wrapper.apply(true,
                    "process_id IN (SELECT id FROM flw_process WHERE process_name LIKE {0})",
                    processNameLike);
        }

        // 业务Key精确筛选
        if (StrUtil.isNotBlank(request.getBusinessKey())) {
            wrapper.eq(FlwInstance::getBusinessKey, request.getBusinessKey());
        }

        // 优先级筛选
        if (request.getPriority() != null) {
            wrapper.eq(FlwInstance::getPriority, request.getPriority());
        }

        // 排序：优先级高的在前，或按创建时间
        if (Boolean.TRUE.equals(request.getSortByPriority())) {
            wrapper.orderByDesc(FlwInstance::getPriority)
                   .orderByDesc(FlwInstance::getCreateTime);
        } else {
            wrapper.orderByDesc(FlwInstance::getCreateTime);
        }

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

        // 业务Key精确筛选
        if (StrUtil.isNotBlank(request.getBusinessKey())) {
            wrapper.eq(FlwHisInstance::getBusinessKey, request.getBusinessKey());
        }

        // 优先级筛选
        if (request.getPriority() != null) {
            wrapper.eq(FlwHisInstance::getPriority, request.getPriority());
        }

        // 流程名称筛选（模糊匹配 flw_process.process_name，兼容 processName / processDefinitionName 两个字段）
        String processNameLike = buildProcessNameLike(request);
        if (processNameLike != null) {
            wrapper.apply(true,
                    "process_id IN (SELECT id FROM flw_process WHERE process_name LIKE {0})",
                    processNameLike);
        }

        // 状态筛选（对照 FlowLong InstanceState 枚举：1=complete 2=reject 3=revoke 4=timeout 5=terminate 6=autoPass 7=autoReject）
        if ("finished".equals(status)) {
            // 已结束: 审批通过(1) + 自动通过(6)
            wrapper.in(FlwHisInstance::getInstanceState, 1, 6);
        } else if ("terminated".equals(status)) {
            // 已终止: 审批拒绝(2) + 撤销(3) + 超时(4) + 强制终止(5) + 自动拒绝(7)
            wrapper.in(FlwHisInstance::getInstanceState, 2, 3, 4, 5, 7);
        } else {
            // 全部已结束: state > 0（即排除审批中/暂停/暂存）
            wrapper.gt(FlwHisInstance::getInstanceState, 0);
        }

        // 排序：优先级高的在前，或按创建时间
        if (Boolean.TRUE.equals(request.getSortByPriority())) {
            wrapper.orderByDesc(FlwHisInstance::getPriority)
                   .orderByDesc(FlwHisInstance::getCreateTime);
        } else {
            wrapper.orderByDesc(FlwHisInstance::getCreateTime);
        }

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
     * 由于 FlowLong 分开存储活动实例(flw_instance)和历史实例(flw_his_instance),
     * 需要分别查询后合并。两表 schema 一致,合并后按 startTime 倒序,内存分页。
     *
     * 注: 当前实现先全量加载再内存分页,适合单租户万级以内的流程数据。
     * 数据量超过 5w 时建议改为 SQL UNION ALL + DB 端分页。
     */
    private Page<ProcessInstanceResponse> queryAllInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        // SQL 分页（UNION ALL 两侧条件下推），替代原"全量捞取+内存排序+内存分页"
        Long processDefinitionId = null;
        if (StrUtil.isNotBlank(request.getProcessDefinitionId())) {
            try {
                processDefinitionId = Long.parseLong(request.getProcessDefinitionId());
            } catch (NumberFormatException ignored) {
            }
        }
        String nameLike = buildProcessNameLike(request);

        Page<Map<String, Object>> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        List<Map<String, Object>> rows = instanceQueryMapper.selectUnionPage(pageParam,
                startUserId == null ? null : String.valueOf(startUserId),
                processDefinitionId,
                nameLike,
                StrUtil.blankToDefault(request.getBusinessKey(), null),
                request.getPriority(),
                Boolean.TRUE.equals(request.getSortByPriority()));

        List<ProcessInstanceResponse> records = rows.stream()
                .map(this::convertUnionRowToResponse)
                .collect(Collectors.toList());

        Page<ProcessInstanceResponse> result = new Page<>(request.getPageNum(), request.getPageSize(), pageParam.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * UNION 行 → 响应对象（列名小写；时间戳由 H2/MySQL 驱动按类型返回 java.sql.Timestamp）
     */
    private ProcessInstanceResponse convertUnionRowToResponse(Map<String, Object> row) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(String.valueOf(row.get("id")));
        Long processId = row.get("process_id") == null ? null : Long.parseLong(String.valueOf(row.get("process_id")));
        if (processId != null) {
            response.setProcessDefinitionId(String.valueOf(processId));
            FlwProcess process = processService.getProcessById(processId);
            if (process != null) {
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }
        }
        Object createTime = row.get("create_time");
        if (createTime instanceof java.sql.Timestamp ts) {
            response.setStartTime(ts.toLocalDateTime());
        }
        Object endTime = row.get("end_time");
        if (endTime instanceof java.sql.Timestamp ts) {
            response.setEndTime(ts.toLocalDateTime());
        }
        response.setProcessNo((String) row.get("instance_no"));
        response.setCurrentActivityName((String) row.get("current_node_name"));
        String createId = (String) row.get("create_id");
        if (StrUtil.isNotBlank(createId)) {
            response.setStartUserId(createId);
            try {
                response.setStartUserName(identityService.getUserName(Long.parseLong(createId)));
            } catch (NumberFormatException e) {
                response.setStartUserName((String) row.get("create_by"));
            }
        }
        response.setBusinessKey((String) row.get("business_key"));
        Object priorityVal = row.get("priority");
        if (priorityVal != null) {
            response.setPriority(Integer.parseInt(String.valueOf(priorityVal)));
        }
        Object urgentVal = row.get("urgent");
        if (urgentVal != null) {
            int urgent = Integer.parseInt(String.valueOf(urgentVal));
            response.setUrgent(urgent);
            response.setUrgentName(getUrgentName(urgent));
        }
        Integer state = row.get("instance_state") == null ? null
                : Integer.parseInt(String.valueOf(row.get("instance_state")));
        // 运行中实例补充当前处理人/候选人（与旧版两侧转换行为对齐）
        if (state == null || state == 0) {
            response.setCurrentActivityName((String) row.get("current_node_name"));
            try {
                fillCurrentAssigneeInfo(response, Long.parseLong(String.valueOf(row.get("id"))));
            } catch (NumberFormatException ignored) {
            }
        }
        // 历史侧状态映射（0=运行中）；终止类状态（2/3/4/5/7）映射 deleteReason 文案
        if (state != null && state > 0) {
            if (state == 2) response.setDeleteReason("审批拒绝");
            else if (state == 3) response.setDeleteReason("撤销审批");
            else if (state == 4) response.setDeleteReason("超时结束");
            else if (state == 5) response.setDeleteReason("强制终止");
            else if (state == 7) response.setDeleteReason("自动拒绝");
        }
        return response;
    }

    private int compareStartTimeDesc(ProcessInstanceResponse a, ProcessInstanceResponse b) {
        if (a.getStartTime() == null && b.getStartTime() == null) return 0;
        if (a.getStartTime() == null) return 1;
        if (b.getStartTime() == null) return -1;
        return b.getStartTime().compareTo(a.getStartTime());
    }

    /**
     * 保存审批意见（实例级动作 taskId 传 null 时回退挂到发起任务，task_id 列非空）
     */
    private void saveApprovalComment(Long processInstanceId, Long taskId,
                                      String taskDefKey, String taskName,
                                      Long userId, String userName,
                                      String actionType, String commentText) {
        Long effectiveTaskId = taskId;
        if (effectiveTaskId == null) {
            FlwHisTask startTask = queryService.getStartTaskByInstanceId(processInstanceId);
            effectiveTaskId = startTask != null ? startTask.getId() : 0L;
        }
        WfApprovalComment comment = new WfApprovalComment();
        comment.setProcessInstanceId(processInstanceId);
        comment.setTaskId(effectiveTaskId);
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
     * 构建审批节点时间线（委托 ApprovalTimelineBuilder：模型线性化 + 任务/意见归组）
     */
    private void buildApprovalNodes(ApprovalDetailResponse detail, Long instanceId, Long processId,
                                    List<FlwHisTask> hisTasks, List<FlwTask> activeTasks) {
        detail.setNodes(approvalTimelineBuilder.build(instanceId, processId, hisTasks, activeTasks));
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

        // 优先级信息
        response.setPriority(instance.getPriority());
        response.setPriorityName(getPriorityName(instance.getPriority()));
        response.setUrgent(instance.getUrgent());
        response.setUrgentName(getUrgentName(instance.getUrgent()));

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
        response.setProcessNo(instance.getInstanceNo());
        // 获取当前任务的受理人/候选人信息
        fillCurrentAssigneeInfo(response, instance.getId());

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

        // 优先级信息
        response.setPriority(hisInstance.getPriority());
        response.setPriorityName(getPriorityName(hisInstance.getPriority()));
        response.setUrgent(hisInstance.getUrgent());
        response.setUrgentName(getUrgentName(hisInstance.getUrgent()));
        // 流程编号
        response.setProcessNo(hisInstance.getInstanceNo());
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

        // 状态判断（对照 FlowLong InstanceState 枚举）
        Integer instanceState = hisInstance.getInstanceState();
        if (instanceState != null) {
            // 2=审批拒绝, 3=撤销审批, 4=超时结束, 5=强制终止, 7=自动拒绝
            response.setDeleteReason(
                    instanceState == 2 ? "审批拒绝"
                    : instanceState == 3 ? "撤销审批"
                    : instanceState == 4 ? "超时结束"
                    : instanceState == 5 ? "强制终止"
                    : instanceState == 7 ? "自动拒绝"
                    : null);
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

    /**
     * 获取优先级名称
     */
    private String getPriorityName(Integer priority) {
        if (priority == null || priority == 0) {
            return "普通";
        } else if (priority == 1) {
            return "高优先级";
        }
        return "未知";
    }

    private String getUrgentName(Integer urgent) {
        if (urgent == null) {
            return "常规";
        }
        return switch (urgent) {
            case 1 -> "重要不紧急";
            case 2 -> "紧急不重要";
            case 3 -> "紧急且重要";
            default -> "常规";
        };
    }

    @Override
    public List<ProcessInstanceResponse> getInstancesByBusinessKey(String businessKey) {
        if (StrUtil.isBlank(businessKey)) {
            throw new BusinessException(400, "业务Key不能为空");
        }

        List<ProcessInstanceResponse> results = new ArrayList<>();

        // 查询运行中的实例
        Optional<List<FlwInstance>> runningInstances = queryService.getInstancesByBusinessKey(businessKey);
        if (runningInstances.isPresent()) {
            for (FlwInstance instance : runningInstances.get()) {
                results.add(convertToResponse(instance));
            }
        }

        // 查询历史实例
        Optional<List<FlwHisInstance>> hisInstances = queryService.getHisInstancesByBusinessKey(businessKey);
        if (hisInstances.isPresent()) {
            for (FlwHisInstance hisInstance : hisInstances.get()) {
                results.add(convertToHisResponse(hisInstance));
            }
        }

        return results;
    }

    @Override
    public ProcessInstanceResponse getInstanceByBusinessKey(String processDefinitionId, String businessKey) {
        if (StrUtil.isBlank(processDefinitionId)) {
            throw new BusinessException(400, "流程定义ID不能为空");
        }
        if (StrUtil.isBlank(businessKey)) {
            throw new BusinessException(400, "业务Key不能为空");
        }

        Long processId = parseProcessId(processDefinitionId);

        // 查询运行中的实例
        LambdaQueryWrapper<FlwInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlwInstance::getProcessId, processId)
               .eq(FlwInstance::getBusinessKey, businessKey);
        FlwInstance instance = instanceMapper.selectOne(wrapper);
        if (instance != null) {
            return convertToResponse(instance);
        }

        // 查询历史实例
        LambdaQueryWrapper<FlwHisInstance> hisWrapper = new LambdaQueryWrapper<>();
        hisWrapper.eq(FlwHisInstance::getProcessId, processId)
                  .eq(FlwHisInstance::getBusinessKey, businessKey);
        FlwHisInstance hisInstance = hisInstanceMapper.selectOne(hisWrapper);
        if (hisInstance != null) {
            return convertToHisResponse(hisInstance);
        }

        return null;
    }
}