package com.forge.modules.workflow.framework.actor;

import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.NodeSetType;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.impl.GeneralTaskActorProvider;
import com.aizuda.bpm.engine.model.NodeAssignee;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
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
        if (nodeModel.getType() != null && nodeModel.getType() == 0) {
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
            // 使用候选人策略计算用户
            BpmTaskCandidateStrategy.TaskContext taskContext = createTaskContext(execution);
            Set<Long> userIds = candidateInvoker.calculateUsers(strategyCode, param, taskContext);

            log.info("候选人计算结果: userIds={}", userIds);

            if (!userIds.isEmpty()) {
                // 转换为 FlowLong 任务参与者
                List<FlwTaskActor> actors = userIds.stream()
                        .map(userId -> {
                            String userName = identityService.getUserName(userId);
                            return FlwTaskActor.ofFlowCreator(FlowCreator.of(String.valueOf(userId), userName));
                        })
                        .collect(Collectors.toList());
                log.info("生成任务参与者: actors.size={}", actors.size());
                return actors;
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

        // 根据 setType 推算策略
        // setType: 1=指定成员, 2=部门负责人(映射到发起人部门负责人), 3=角色, 4=发起人自选, 5=发起人自己, 6=审批人自选, 7=连续多级部门负责人, 8=表达式
        // 注意：前端设计器 setType=2 对应"部门负责人"，FlowLong 没有直接对应值，使用 START_USER_DEPT_LEADER(37)
        if (setType != null) {
            switch (setType) {
                case 1: return 30; // 指定成员 -> USER
                case 2: return 37; // 部门负责人 -> START_USER_DEPT_LEADER（发起人的部门负责人）
                case 3: return 10; // 角色 -> ROLE
                case 4: return 35; // 发起人自选 -> START_USER_SELECT
                case 5: return 36; // 发起人自己 -> START_USER
                case 6: return 34; // 审批人自选 -> APPROVE_USER_SELECT
                case 7: return 38; // 连续多级部门负责人 -> DEPT_LEADER_MULTI
                case 8: return 60; // 表达式 -> EXPRESSION
            }
        }

        return null;
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
                FlowCreator flowCreator = execution.getFlowCreator();
                if (flowCreator != null && flowCreator.getCreateId() != null) {
                    try {
                        return Long.parseLong(flowCreator.getCreateId());
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