package com.forge.modules.workflow.service;

import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.forge.modules.workflow.dto.ai.AiRoutingConfig;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI智能路由服务：网关分支决策（条件/包容/路由网关）
 *
 * @author forge-admin
 */
public interface WfAiRoutingService {

    /**
     * 解析网关节点的 AI 路由配置（extendConfig.aiRouting）
     *
     * @return null 表示未配置或解析失败（视为未启用，走规则判断）
     */
    AiRoutingConfig parseRoutingConfig(NodeModel nodeModel);

    /**
     * 执行一次路由决策：构建提示词 → 限时调用 LLM → 解析匹配候选分支 → 审计落库
     * <p>
     * 任何失败（超时/异常/解析失败/低置信度）均不抛异常，返回对应状态的决策对象且 matchedNodeKeys 为空，
     * 由调用方（FlowAiHandler）返回 null 触发引擎原生降级链（SpEL 条件 → 默认分支）
     *
     * @param nodeModel  网关节点模型
     * @param candidates 候选分支（含默认分支）
     * @param inclusive  true 包容网关多选；false 条件/路由网关单选
     * @param instanceId 流程实例ID（审计用，可为 null）
     * @param args       流程变量
     */
    RoutingDecision decide(NodeModel nodeModel, List<ConditionNode> candidates,
                           boolean inclusive, Long instanceId, Map<String, Object> args);

    /**
     * 路由决策结果
     */
    @Data
    class RoutingDecision {

        /**
         * 决策状态：SUCCESS / FALLBACK / LOW_CONFIDENCE / TIMEOUT / ERROR
         */
        private String status;

        /**
         * 匹配到候选的 nodeKey 列表（仅 status=SUCCESS 时非空；单选网关最多一个）
         */
        private List<String> matchedNodeKeys;

        /**
         * AI 原始选中的分支标识（nodeKey 或分支名，未匹配候选时保留原样，供审计）
         */
        private List<String> rawSelections;

        private Integer confidence;

        private String reasoning;

        private String rawResponse;

        private String provider;

        private String modelName;
    }
}
