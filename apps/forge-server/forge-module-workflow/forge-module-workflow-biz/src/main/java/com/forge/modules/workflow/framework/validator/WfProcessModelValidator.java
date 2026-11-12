package com.forge.modules.workflow.framework.validator;

import com.aizuda.bpm.engine.assist.ObjectUtils;
import com.aizuda.bpm.engine.core.enums.NodeSetType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.ModelHelper;
import com.aizuda.bpm.engine.model.NodeModel;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.forge.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 流程模型节点校验器（发起前模型合法性校验，自 WfProcessInstanceServiceImpl 平移）
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class WfProcessModelValidator {

    private final ProcessService processService;

    /**
     * 递归检查节点模型，跳过已通过 FlowDataTransfer 传递审批人的发起人自选节点
     */
    public void checkNodeModelWithDynamicAssignee(NodeModel rootNode, Set<String> dynamicAssigneeNodeKeys) {
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

            // 审批/抄送/条件审批节点处理人配置校验（原三段 setType 校验合并为一法，文案前缀参数化逐字保留）
            if ((TaskType.approval.eq(node.getType()) || TaskType.cc.eq(node.getType())
                    || TaskType.conditionNode.eq(node.getType()))
                    && ObjectUtils.isEmpty(node.getNodeAssigneeList())) {
                if (TaskType.approval.eq(node.getType())) {
                    checkAssigneeConfigured("审批节点", "发起人自选节点", node, dynamicAssigneeNodeKeys);
                } else if (TaskType.cc.eq(node.getType())) {
                    checkAssigneeConfigured("抄送节点", "抄送发起人自选节点", node, dynamicAssigneeNodeKeys);
                } else {
                    // 条件审批节点无发起人自选分支
                    checkAssigneeConfigured("条件审批节点", null, node, dynamicAssigneeNodeKeys);
                }
            }

            // 其他校验（自动通过、自动拒绝、路由、子流程等）
            if (TaskType.autoPass.eq(node.getType()) || TaskType.autoReject.eq(node.getType())) {
                if (!ModelHelper.inConditionNode(node) || node.getChildNode() != null) {
                    throw new BusinessException(400, "自动通过/拒绝节点「" + node.getNodeName() + "」配置错误");
                }
            }
            if (node.routeNode() && ObjectUtils.isEmpty(node.getRouteNodes())) {
                throw new BusinessException(400, "路由节点「" + node.getNodeName() + "」未配置路由分支");
            }
            if (node.callProcessNode() && ObjectUtils.isEmpty(node.getCallProcess())) {
                throw new BusinessException(400, "子流程节点「" + node.getNodeName() + "」未选择子流程");
            }

            // AI 智能路由网关校验
            checkAiRoutingGateway(node);

            // 条件审批节点（type=3）：条件列表（从 extendConfig.conditionList 读取；审批人校验已在上方合并处理）
            if (TaskType.conditionNode.eq(node.getType())) {
                Map<String, Object> extendConfig = node.getExtendConfig();
                Object condListObj = extendConfig == null ? null : extendConfig.get("conditionList");
                if (!(condListObj instanceof List) || ((List<?>) condListObj).isEmpty()) {
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
                List<ConditionNode> parallelNodes = node.getParallelNodes();
                if (parallelNodes == null || parallelNodes.size() < 2) {
                    throw new BusinessException(400, "并行分支「" + node.getNodeName() + "」至少需要 2 个分支");
                }
            }

            // 包容分支：至少 1 条无条件兜底分支（引擎 defaultConditionNode 取第一个空条件分支，缺失时全不满足会异常）
            if (node.inclusiveNode()) {
                List<ConditionNode> inclusiveNodes = node.getInclusiveNodes();
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
                for (ConditionNode rn : node.getRouteNodes()) {
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
     * 审批人配置校验（原 approval/cc/conditionNode 三段近乎逐字复制的 setType 校验收敛为一法）。
     * nodeKindLabel/initiatorSelectedLabel 参数化异常文案前缀（逐字保留原文案）；
     * 条件审批节点无发起人自选分支（initiatorSelectedLabel 传 null 跳过）。
     */
    private void checkAssigneeConfigured(String nodeKindLabel, String initiatorSelectedLabel,
                                         NodeModel node, Set<String> dynamicAssigneeNodeKeys) {
        if (NodeSetType.specifyMembers.eq(node.getSetType())) {
            // 抄送节点指定成员且允许自选（allowSelection=true）时不校验
            if (TaskType.cc.eq(node.getType()) && Boolean.TRUE.equals(node.getAllowSelection())) {
                return;
            }
            throw new BusinessException(400, nodeKindLabel + "「" + node.getNodeName() + "」未配置处理人员");
        }
        if (NodeSetType.role.eq(node.getSetType())) {
            throw new BusinessException(400, nodeKindLabel + "「" + node.getNodeName() + "」未选择角色");
        }
        if (initiatorSelectedLabel != null && NodeSetType.initiatorSelected.eq(node.getSetType())) {
            // 发起人自选：检查是否已通过 FlowDataTransfer 传递审批人
            if (!dynamicAssigneeNodeKeys.contains(node.getNodeKey())) {
                throw new BusinessException(400, initiatorSelectedLabel + "「" + node.getNodeName() + "」未选择审批人");
            }
        }
    }

    /**
     * AI 智能路由网关校验：仅分支节点可启用；启用后必须保留无条件默认分支作为降级路径
     * （AI 失败/超时/低置信度时引擎落回 SpEL 条件，均不中而无默认分支会抛 Not found executable ConditionNode）
     */
    public void checkAiRoutingGateway(NodeModel node) {
        if (node.getCallAi() == null) {
            return;
        }
        if (!node.conditionNode() && !node.inclusiveNode() && !node.routeNode()) {
            throw new BusinessException(400, "节点「" + node.getNodeName() + "」配置了AI路由，但仅分支节点支持AI路由");
        }
        List<ConditionNode> aiBranches = node.routeNode() ? node.getRouteNodes()
                : node.inclusiveNode() ? node.getInclusiveNodes() : node.getConditionNodes();
        boolean hasDefaultBranch = aiBranches != null && aiBranches.stream()
                .anyMatch(b -> ObjectUtils.isEmpty(b.getConditionList()));
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
            List<ConditionNode> conditionNodes = node.getConditionNodes();
            if (conditionNodes != null) {
                for (ConditionNode cn : conditionNodes) {
                    collectNodes(cn.getChildNode(), nodes);
                }
            }
            collectNodes(node.getChildNode(), nodes);
        }
        // 处理并行分支
        else if (node.parallelNode()) {
            List<ConditionNode> parallelNodes = node.getParallelNodes();
            if (parallelNodes != null) {
                for (ConditionNode pn : parallelNodes) {
                    collectNodes(pn.getChildNode(), nodes);
                }
            }
            collectNodes(node.getChildNode(), nodes);
        }
        // 处理包容分支
        else if (node.inclusiveNode()) {
            List<ConditionNode> inclusiveNodes = node.getInclusiveNodes();
            if (inclusiveNodes != null) {
                for (ConditionNode in : inclusiveNodes) {
                    collectNodes(in.getChildNode(), nodes);
                }
            }
            collectNodes(node.getChildNode(), nodes);
        }
        // 处理路由分支（路由分支子链不执行，但参与节点收集以便 KEY 查重与目标校验）
        else if (node.routeNode()) {
            List<ConditionNode> routeNodes = node.getRouteNodes();
            if (routeNodes != null) {
                for (ConditionNode rn : routeNodes) {
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

    private void requireField(Map<String, Object> extendConfig, String field, String nodeName, String mode) {
        Object value = extendConfig == null ? null : extendConfig.get(field);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new BusinessException(400,
                    "触发器任务节点「" + nodeName + "」(" + mode + ") 未配置 " + field);
        }
    }
}
