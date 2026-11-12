package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.*;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.core.enums.TaskState;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.engine.model.DynamicAssignee;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.modules.workflow.dto.instance.AiDecisionResponse;
import com.forge.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.modules.workflow.entity.WfAiApprovalRecord;
import com.forge.modules.workflow.entity.WfAiRouteRecord;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.framework.ApprovalActionTypeEnum;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.framework.WorkflowPermissionSupport;
import com.forge.modules.workflow.framework.instance.ProcessInstanceAssembler;
import com.forge.modules.workflow.framework.instance.WfApprovalCommentService;
import com.forge.modules.workflow.framework.instance.WfInstanceQueryService;
import com.forge.modules.workflow.framework.validator.WfProcessModelValidator;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.framework.diagram.FlowLongDiagramGenerator;
import com.forge.modules.workflow.framework.title.InstanceTitleRenderer;
import com.forge.modules.workflow.framework.timeline.ApprovalTimelineBuilder;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.forge.modules.workflow.mapper.WfAiApprovalRecordMapper;
import com.forge.modules.workflow.mapper.WfAiRouteRecordMapper;
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
    private final FlowLongDiagramGenerator diagramGenerator;
    private final FlowLongIdentityService identityService;
    private final WfAiRouteRecordMapper aiRouteRecordMapper;
    private final WfAiApprovalRecordMapper aiApprovalRecordMapper;
    private final WfProcessExtMapper processExtMapper;
    private final ProcessNoGenerator processNoGenerator;
    private final ApprovalTimelineBuilder approvalTimelineBuilder;
    private final ObjectMapper objectMapper;
    private final WfProcessModelValidator processModelValidator;
    private final WfInstanceQueryService instanceQueryService;
    private final ProcessInstanceAssembler processInstanceAssembler;
    private final WfApprovalCommentService approvalCommentService;

    @Override
    public Page<ProcessInstanceResponse> pageInstance(ProcessInstanceQueryRequest request) {
        return instanceQueryService.queryInstances(request, null);
    }

    @Override
    public Page<ProcessInstanceResponse> getMyInstances(ProcessInstanceQueryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }
        return instanceQueryService.queryInstances(request, currentUserId);
    }

    @Override
    public ProcessInstanceResponse getInstanceById(String processInstanceId) {
        Long id = parseInstanceId(processInstanceId);

        // 先尝试获取活动实例
        FlwInstance instance = queryService.getInstance(id);
        if (instance != null) {
            return processInstanceAssembler.convertToResponse(instance);
        }

        // 获取历史实例
        FlwHisInstance hisInstance = queryService.getHistInstance(id);
        if (hisInstance == null) {
            throw new BusinessException(404, "流程实例不存在");
        }

        return processInstanceAssembler.convertToHisResponse(hisInstance);
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

            // 流程标题：显式传入优先（业务模块覆盖场景），否则按模型标题规则渲染；存实例变量 __title
            String explicitTitle = request.getTitle() == null ? null : request.getTitle().trim();
            if (explicitTitle != null && !explicitTitle.isEmpty()) {
                variables.put(InstanceTitleRenderer.TITLE_VARIABLE, explicitTitle);
            } else {
                WfProcessExt startExt = getProcessExtByProcessId(processId);
                if (startExt != null && startExt.getTitleRule() != null && !startExt.getTitleRule().isBlank()) {
                    String title = InstanceTitleRenderer.render(startExt.getTitleRule(),
                            process.getProcessName(), identityService.getUserName(currentUserId),
                            businessKey, variables);
                    if (title != null) {
                        variables.put(InstanceTitleRenderer.TITLE_VARIABLE, title);
                    }
                }
            }

            // 自定义模型校验：跳过已通过 FlowDataTransfer 传递审批人的发起人自选节点
            // 使用 supplier 创建带有 businessKey 和 priority 的 FlwInstance
            flowLongEngine.startInstanceById(processId, flowCreator, variables, false, nodeModel -> {
                // 递归检查所有节点（模型校验器）
                processModelValidator.checkNodeModelWithDynamicAssignee(nodeModel, dynamicAssigneeNodeKeys);
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

                approvalCommentService.saveApprovalComment(
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
        approvalCommentService.saveApprovalComment(
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

        approvalCommentService.saveApprovalComment(id, null, "resume", "唤醒", currentUserId,
                identityService.getUserName(currentUserId),
                ApprovalActionTypeEnum.RESUME.getCode(), "唤醒流程实例，从上次中断节点继续");
        log.info("流程实例已唤醒：instanceId={}, operator={}", id, currentUserId);
    }

    private boolean hasWorkflowAdminRole() {
        return WorkflowPermissionSupport.hasWorkflowAdminRole();
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
                        new tools.jackson.core.type.TypeReference<List<Long>>() {});
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
        return approvalCommentService.getApprovalComments(parseInstanceId(processInstanceId));
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
        detail.setStartTime(processInstanceAssembler.toLocalDateTime(instance != null ? instance.getCreateTime() : hisInstance.getCreateTime()));
        detail.setEndTime(processInstanceAssembler.toLocalDateTime(hisInstance != null ? hisInstance.getEndTime() : null));
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
                results.add(processInstanceAssembler.convertToResponse(instance));
            }
        }

        // 查询历史实例
        Optional<List<FlwHisInstance>> hisInstances = queryService.getHisInstancesByBusinessKey(businessKey);
        if (hisInstances.isPresent()) {
            for (FlwHisInstance hisInstance : hisInstances.get()) {
                results.add(processInstanceAssembler.convertToHisResponse(hisInstance));
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
            return processInstanceAssembler.convertToResponse(instance);
        }

        // 查询历史实例
        LambdaQueryWrapper<FlwHisInstance> hisWrapper = new LambdaQueryWrapper<>();
        hisWrapper.eq(FlwHisInstance::getProcessId, processId)
                  .eq(FlwHisInstance::getBusinessKey, businessKey);
        FlwHisInstance hisInstance = hisInstanceMapper.selectOne(hisWrapper);
        if (hisInstance != null) {
            return processInstanceAssembler.convertToHisResponse(hisInstance);
        }

        return null;
    }
}
