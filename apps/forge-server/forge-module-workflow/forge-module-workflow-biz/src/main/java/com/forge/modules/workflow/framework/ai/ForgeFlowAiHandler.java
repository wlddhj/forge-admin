package com.forge.modules.workflow.framework.ai;

import com.aizuda.bpm.engine.core.Execution;
import com.aizuda.bpm.engine.core.FlowLongContext;
import com.aizuda.bpm.engine.handler.FlowAiHandler;
import com.aizuda.bpm.engine.model.AiResponse;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.modules.workflow.service.WfAiRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * FlowLong 引擎 AI 智能体处理器（forge 实现）
 *
 * <p>路由职责：条件(4)/路由(23)网关经 {@link #decideRoute} 单选分支，包容(9)网关经
 * {@link #decideInclusiveRoutes} 多选分支；决策失败一律返回 null，落回引擎原生
 * 降级链（SpEL 条件 → 默认分支）。
 *
 * <p>审批职责：forge 的 AI 审批走 extendConfig.aiApproval + 任务监听器事件路径
 * （见 AiApprovalExecutor），不使用引擎 callAi 审批回调，此处 {@link #execute}
 * 仅作 no-op 兜底并告警，防止两条路径语义分裂。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForgeFlowAiHandler implements FlowAiHandler {

    private final WfAiRoutingService aiRoutingService;

    @Override
    public String decideRoute(FlowLongContext flowLongContext, Execution execution,
                              NodeModel nodeModel, Map<String, Object> args) {
        List<String> nodeKeys = decide(nodeModel, execution, args, false);
        return nodeKeys == null || nodeKeys.isEmpty() ? null : nodeKeys.get(0);
    }

    @Override
    public List<String> decideInclusiveRoutes(FlowLongContext flowLongContext, Execution execution,
                                              NodeModel nodeModel, Map<String, Object> args) {
        return decide(nodeModel, execution, args, true);
    }

    private List<String> decide(NodeModel nodeModel, Execution execution,
                                Map<String, Object> args, boolean inclusive) {
        try {
            List<ConditionNode> candidates = resolveCandidates(nodeModel, inclusive);
            if (candidates == null || candidates.isEmpty()) {
                log.warn("AI路由网关无候选分支，走规则降级: nodeKey={}", nodeModel.getNodeKey());
                return null;
            }
            Long instanceId = execution != null && execution.getFlwInstance() != null
                    ? execution.getFlwInstance().getId() : null;
            WfAiRoutingService.RoutingDecision decision =
                    aiRoutingService.decide(nodeModel, candidates, inclusive, instanceId, args);
            if ("SUCCESS".equals(decision.getStatus())
                    && decision.getMatchedNodeKeys() != null && !decision.getMatchedNodeKeys().isEmpty()) {
                return decision.getMatchedNodeKeys();
            }
            return null;
        } catch (Exception e) {
            log.warn("AI路由决策异常，走规则降级: nodeKey={}, error={}", nodeModel.getNodeKey(), e.getMessage(), e);
            return null;
        }
    }

    private List<ConditionNode> resolveCandidates(NodeModel nodeModel, boolean inclusive) {
        if (inclusive) {
            return nodeModel.getInclusiveNodes();
        }
        if (nodeModel.routeNode()) {
            return nodeModel.getRouteNodes();
        }
        return nodeModel.getConditionNodes();
    }

    @Override
    public AiResponse execute(FlowLongContext flowLongContext, Execution execution, NodeModel nodeModel) {
        log.warn("引擎 callAi 审批路径未启用（forge AI审批走 extendConfig.aiApproval 监听器）: nodeKey={}",
                nodeModel.getNodeKey());
        return AiResponse.success(null, null, null);
    }

    @Override
    public boolean processAiResponse(FlowLongContext flowLongContext, Execution execution,
                                     NodeModel nodeModel, AiResponse aiResponse) {
        // 任务照常创建，交由人工或 AI 审批监听器处理
        return true;
    }
}
