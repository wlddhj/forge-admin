package com.forge.modules.workflow.framework.actor;

import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.NodeSetType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.impl.GeneralTaskActorProvider;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.framework.candidate.CandidateStrategyEnum;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FlowLong 任务参与者提供处理类
 *
 * 实现候选人策略计算和权限判断
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ForgeTaskActorProvider extends GeneralTaskActorProvider {

    private final BpmTaskCandidateInvoker candidateInvoker;
    private final FlowLongIdentityService identityService;

    public ForgeTaskActorProvider(BpmTaskCandidateInvoker candidateInvoker,
                                   FlowLongIdentityService identityService) {
        this.candidateInvoker = candidateInvoker;
        this.identityService = identityService;
    }

    /**
     * 判断流程创建者是否允许操作执行当前节点
     *
     * 对于角色、部门等类型的节点，需要检查当前用户是否在对应的角色/部门中
     */
    @Override
    public boolean isAllowed(NodeModel nodeModel, FlowCreator flowCreator) {
        List<NodeAssignee> nodeAssigneeList = nodeModel.getNodeAssigneeList();

        // 如果是指定成员类型，检查用户是否在成员列表中
        if (NodeSetType.specifyMembers.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeAssigneeList)) {
            return nodeAssigneeList.stream()
                    .anyMatch(t -> Objects.equals(t.getId(), flowCreator.getCreateId()));
        }

        // 如果是角色类型，需要检查用户是否拥有该角色
        if (NodeSetType.role.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeAssigneeList)) {
            Long userId = Long.parseLong(flowCreator.getCreateId());
            Set<Long> userRoleIds = identityService.getUserRoleIds(userId);

            return nodeAssigneeList.stream()
                    .anyMatch(t -> userRoleIds.contains(Long.parseLong(t.getId())));
        }

        // 如果是部门类型，需要检查用户是否在该部门中
        if (NodeSetType.department.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeAssigneeList)) {
            Long userId = Long.parseLong(flowCreator.getCreateId());
            Long userDeptId = identityService.getUserDeptId(userId);

            return nodeAssigneeList.stream()
                    .anyMatch(t -> Objects.equals(t.getId(), String.valueOf(userDeptId)));
        }

        // 发起人节点，允许
        if (TaskType.major.eq(nodeModel.getType())) {
            return true;
        }

        // 其他情况（如发起人自选、表达式等），允许通过
        return true;
    }

    /**
     * 根据节点模型获取任务参与者
     *
     * 这里使用候选人策略来计算实际的审批人
     */
    @Override
    public List<FlwTaskActor> getTaskActors(NodeModel nodeModel, Execution execution) {
        log.info("开始计算任务参与者: nodeKey={}, nodeName={}, setType={}, nodeAssigneeList={}",
                nodeModel.getNodeKey(), nodeModel.getNodeName(), nodeModel.getSetType(),
                nodeModel.getNodeAssigneeList() != null ? nodeModel.getNodeAssigneeList().size() : 0);

        // 先尝试使用候选人策略计算
        Integer strategyCode = getStrategyFromNodeModel(nodeModel);
        String param = getParamFromNodeModel(nodeModel);

        log.info("候选人策略: strategyCode={}, param={}", strategyCode, param);

        if (strategyCode != null) {
            // 使用候选人策略计算用户。
            // 租户注入（评审 §4.5-P1#5）：策略经 sysUserMapper 按租户查询，此前依赖请求线程
            // UserContext/租户上下文——AI 审批推进/超时自动办理/AFTER_COMMIT 抄送等异步链路
            // 无该上下文，NPE 或错配。此处以实例行租户（权威来源）快照包裹全部策略执行，
            // web 链路（上下文已有租户）为同值 no-op
            Long instanceTenantId = parseTenantId(execution.getFlwInstance() != null
                    ? execution.getFlwInstance().getTenantId() : null);
            Long previousTenantId = TenantContextHolder.getTenantId();
            if (instanceTenantId != null) {
                TenantContextHolder.setTenantId(instanceTenantId);
            }
            try {
                BpmTaskCandidateStrategy.TaskContext taskContext = createTaskContext(execution);
                Set<Long> userIds = candidateInvoker.calculateUsers(strategyCode, param, taskContext);

                log.info("候选人计算结果: userIds={}", userIds);

                if (!userIds.isEmpty()) {
                    // 转换为 FlowLong 任务参与者
                    List<FlwTaskActor> actors = userIds.stream()
                            .map(userId -> {
                                String userName = identityService.getUserName(userId);
                                return FlwTaskActor.ofFlowCreator(FlowCreatorFactory.user(userId, userName));
                            })
                            .collect(Collectors.toList());
                    log.info("生成任务参与者: actors.size={}", actors.size());
                    return actors;
                }
            } finally {
                if (previousTenantId != null) {
                    TenantContextHolder.setTenantId(previousTenantId);
                } else {
                    TenantContextHolder.clear();
                }
            }
        }

        // 如果候选人策略没有计算出结果，使用父类默认逻辑
        List<FlwTaskActor> defaultActors = super.getTaskActors(nodeModel, execution);
        if (defaultActors != null && !defaultActors.isEmpty()) {
            log.info("使用父类默认逻辑获取参与者: defaultActors.size={}", defaultActors.size());
            return defaultActors;
        }

        // 如果指定了审批人但计算结果为空，记录警告
        if (nodeModel.getNodeAssigneeList() != null && !nodeModel.getNodeAssigneeList().isEmpty()) {
            log.warn("节点 {} 无法计算候选人，strategy={}, param={}, assigneeList={}",
                    nodeModel.getNodeName(), strategyCode, param, nodeModel.getNodeAssigneeList());
        } else {
            log.warn("节点 {} 未设置审批人，setType={}", nodeModel.getNodeName(), nodeModel.getSetType());
        }

        return null;
    }

    /**
     * 从节点模型获取候选人策略
     */
    private Integer getStrategyFromNodeModel(NodeModel nodeModel) {
        Integer setType = nodeModel.getSetType();
        log.debug("节点 {}, setType={}", nodeModel.getNodeName(), setType);

        // 使用统一转换方法
        CandidateStrategyEnum strategy = CandidateStrategyEnum.fromSetType(setType);
        return strategy != null ? strategy.getCode() : null;
    }

    /**
     * 从节点模型获取候选人参数
     */
    private String getParamFromNodeModel(NodeModel nodeModel) {
        Map<String, Object> extendConfig = nodeModel.getExtendConfig();
        if (extendConfig != null) {
            Object paramObj = extendConfig.get("candidateParam");
            if (paramObj != null) {
                return paramObj.toString();
            }
        }

        // 对于指定成员类型，使用 nodeAssigneeList 的 ID 作为参数
        if (NodeSetType.specifyMembers.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeModel.getNodeAssigneeList())) {
            return nodeModel.getNodeAssigneeList().stream()
                    .map(NodeAssignee::getId)
                    .collect(Collectors.joining(","));
        }

        // 对于角色类型，使用角色 ID
        if (NodeSetType.role.eq(nodeModel.getSetType()) && ObjectUtils.isNotEmpty(nodeModel.getNodeAssigneeList())) {
            return nodeModel.getNodeAssigneeList().stream()
                    .map(NodeAssignee::getId)
                    .collect(Collectors.joining(","));
        }

        return null;
    }

    /** 引擎实例行租户为 VARCHAR 数字串，转 Long；null/非数字返回 null（上下文不动） */
    private Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(tenantId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 创建任务上下文
     */
    private BpmTaskCandidateStrategy.TaskContext createTaskContext(Execution execution) {
        return new BpmTaskCandidateStrategy.TaskContext() {
            @Override
            public Long getTaskId() {
                return null; // 任务尚未创建
            }

            @Override
            public String getTaskKey() {
                return null;
            }

            @Override
            public String getTaskName() {
                return null;
            }

            @Override
            public Long getInstanceId() {
                return execution.getFlwInstance() != null ? execution.getFlwInstance().getId() : null;
            }

            @Override
            public Long getProcessId() {
                return execution.getFlwInstance() != null ? execution.getFlwInstance().getProcessId() : null;
            }

            @Override
            public Long getStartUserId() {
                // 发起人取实例行 createId（评审 §4.5-P1#6）：flowCreator 是"触发本次流转的
                // 操作者"，仅发起时等于发起人——第二级起"发起人自己(36)/发起人部门负责人(37)/
                // ${initiator}"节点此前会把上一环节审批人误当发起人
                if (execution.getFlwInstance() != null && execution.getFlwInstance().getCreateId() != null) {
                    try {
                        return Long.parseLong(execution.getFlwInstance().getCreateId());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> getVariables() {
                if (execution.getArgs() != null) {
                    return new HashMap<>(execution.getArgs());
                }
                return Collections.emptyMap();
            }

            @Override
            public String getBusinessKey() {
                return execution.getFlwInstance() != null ? execution.getFlwInstance().getBusinessKey() : null;
            }
        };
    }

    /**
     * 对象工具类（复制自 FlowLong）
     */
    private static class ObjectUtils {
        public static boolean isEmpty(Collection<?> collection) {
            return collection == null || collection.isEmpty();
        }

        public static boolean isNotEmpty(Collection<?> collection) {
            return !isEmpty(collection);
        }
    }
}