package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.model.ConditionNode;
import com.aizuda.bpm.engine.model.NodeModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.service.AiModelResolver;
import com.forge.modules.workflow.dto.ai.AiRoutingConfig;
import com.forge.modules.workflow.entity.WfAiRouteRecord;
import com.forge.modules.workflow.mapper.WfAiRouteRecordMapper;
import com.forge.modules.workflow.service.WfAiRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * AI智能路由服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfAiRoutingServiceImpl implements WfAiRoutingService {

    private final LlmClient llmClient;
    private final AiModelResolver modelResolver;
    private final ObjectMapper objectMapper;
    private final WfAiRouteRecordMapper routeRecordMapper;

    @Override
    public AiRoutingConfig parseRoutingConfig(NodeModel nodeModel) {
        if (nodeModel == null || nodeModel.getExtendConfig() == null) {
            return null;
        }
        Object configObj = nodeModel.getExtendConfig().get("aiRouting");
        if (configObj == null) {
            return null;
        }
        try {
            if (configObj instanceof Map) {
                return objectMapper.convertValue(configObj, AiRoutingConfig.class);
            } else if (configObj instanceof String) {
                return objectMapper.readValue((String) configObj, AiRoutingConfig.class);
            }
        } catch (Exception e) {
            log.warn("解析AI路由配置失败: nodeKey={}, error={}", nodeModel.getNodeKey(), e.getMessage());
        }
        return null;
    }

    @Override
    public RoutingDecision decide(NodeModel nodeModel, List<ConditionNode> candidates,
                                  boolean inclusive, Long instanceId, Map<String, Object> args) {
        RoutingDecision decision = new RoutingDecision();
        decision.setMatchedNodeKeys(Collections.emptyList());
        decision.setRawSelections(Collections.emptyList());
        if (candidates == null || candidates.isEmpty()) {
            decision.setStatus("FALLBACK");
            return decision;
        }

        AiRoutingConfig config = parseRoutingConfig(nodeModel);
        if (config == null) {
            log.warn("AI路由已标记(callAi)但 extendConfig.aiRouting 缺失或不可解析，走规则降级: nodeKey={}", nodeModel.getNodeKey());
            decision.setStatus("FALLBACK");
            saveRecord(nodeModel, candidates, decision, instanceId);
            return decision;
        }

        ChatRequest chatRequest = buildChatRequest(config, nodeModel, candidates, inclusive, args);
        ChatResponse response = executeWithTimeout(config, chatRequest, decision);
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            if (!"TIMEOUT".equals(decision.getStatus())) {
                decision.setStatus("ERROR");
                if (response != null && StrUtil.isNotBlank(response.getErrorMessage())) {
                    decision.setReasoning("LLM调用失败：" + response.getErrorMessage());
                }
            }
            log.warn("AI路由调用失败，走规则降级: nodeKey={}, status={}", nodeModel.getNodeKey(), decision.getStatus());
            saveRecord(nodeModel, candidates, decision, instanceId);
            return decision;
        }

        decision.setRawResponse(response.getContent());
        decision.setModelName(response.getModelName());
        parseAndMatch(decision, response.getContent(), candidates, inclusive, config);

        if ("SUCCESS".equals(decision.getStatus())) {
            log.info("AI路由决策: nodeKey={}, instanceId={}, selected={}, confidence={}, reasoning={}",
                    nodeModel.getNodeKey(), instanceId, decision.getMatchedNodeKeys(),
                    decision.getConfidence(), decision.getReasoning());
        } else {
            log.info("AI路由未命中({})，走规则降级: nodeKey={}, instanceId={}, reasoning={}",
                    decision.getStatus(), nodeModel.getNodeKey(), instanceId, decision.getReasoning());
        }
        saveRecord(nodeModel, candidates, decision, instanceId);
        return decision;
    }

    /**
     * 按节点 timeoutSeconds 限时执行 LLM 路由决策
     * <p>
     * 模型解析在调用线程完成（保持租户上下文，DB 快查），仅 LLM 调用异步限时
     */
    private ChatResponse executeWithTimeout(AiRoutingConfig config, ChatRequest chatRequest, RoutingDecision decision) {
        AiModelConfig modelConfig;
        try {
            modelConfig = modelResolver.resolve(chatRequest);
        } catch (Exception e) {
            decision.setStatus("ERROR");
            decision.setReasoning("AI模型配置解析异常：" + e.getMessage());
            return null;
        }
        if (modelConfig == null) {
            decision.setStatus("ERROR");
            decision.setReasoning("未找到可用的AI模型配置");
            return null;
        }
        decision.setProvider(modelConfig.getProvider());

        int timeoutSeconds = config.getTimeoutSecondsOrDefault();
        AiModelConfig resolvedModel = modelConfig;
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(() ->
                llmClient.chat(chatRequest, resolvedModel));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            decision.setStatus("TIMEOUT");
            decision.setReasoning("AI路由调用超时（" + timeoutSeconds + "秒）");
            return null;
        } catch (Exception e) {
            future.cancel(true);
            decision.setStatus("ERROR");
            decision.setReasoning("AI路由调用异常：" + e.getMessage());
            return null;
        }
    }

    private ChatRequest buildChatRequest(AiRoutingConfig config, NodeModel nodeModel,
                                         List<ConditionNode> candidates, boolean inclusive,
                                         Map<String, Object> args) {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setModelName(config.getModelName());
        chatRequest.setTemperature(0.2);
        chatRequest.setContent(buildUserPrompt(config, nodeModel, candidates, args));

        ChatRequest.MessageItem systemMsg = new ChatRequest.MessageItem();
        systemMsg.setRole("system");
        systemMsg.setContent(buildSystemPrompt(inclusive));
        chatRequest.setMessages(List.of(systemMsg));
        return chatRequest;
    }

    private String buildSystemPrompt(boolean inclusive) {
        if (inclusive) {
            return "你是工作流包容分支路由助手。根据流程数据判断哪些分支需要同时执行，可以选中多个分支。\n" +
                    "你必须以JSON格式返回结果，格式如下：\n" +
                    "{\"nodeKeys\": [\"分支nodeKey1\", \"分支nodeKey2\"], \"confidence\": 0-100, \"reasoning\": \"路由理由\"}\n" +
                    "nodeKeys 只能从候选分支的 nodeKey 中选择，至少选择一个。";
        }
        return "你是工作流分支路由助手。根据流程数据判断流程应该进入哪条分支。\n" +
                "你必须以JSON格式返回结果，格式如下：\n" +
                "{\"nodeKeys\": [\"分支nodeKey\"], \"confidence\": 0-100, \"reasoning\": \"路由理由\"}\n" +
                "nodeKeys 只能从候选分支的 nodeKey 中选择，且只能选择一个最匹配的分支。";
    }

    private String buildUserPrompt(AiRoutingConfig config, NodeModel nodeModel,
                                   List<ConditionNode> candidates, Map<String, Object> args) {
        StringBuilder prompt = new StringBuilder();
        if (StrUtil.isNotBlank(config.getPrompt())) {
            prompt.append("路由规则：").append(config.getPrompt()).append("\n\n");
        }
        prompt.append("当前网关节点：").append(nodeModel.getNodeName()).append("\n");
        prompt.append("候选分支：\n");
        for (ConditionNode candidate : candidates) {
            prompt.append("- nodeKey=").append(candidate.getNodeKey())
                    .append("，名称=").append(StrUtil.blankToDefault(candidate.getNodeName(), candidate.getNodeKey()))
                    .append("\n");
        }
        prompt.append("\n流程变量：\n");
        if (args != null && !args.isEmpty()) {
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                if (!isSensitiveField(entry.getKey())) {
                    prompt.append("- ").append(entry.getKey()).append(": ")
                            .append(formatValue(entry.getValue())).append("\n");
                }
            }
        } else {
            prompt.append("（无）\n");
        }
        prompt.append("\n请判断应进入的分支并以JSON格式返回。");
        return prompt.toString();
    }

    /**
     * 解析 LLM 响应并匹配候选分支：nodeKey 优先，分支名兜底
     */
    void parseAndMatch(RoutingDecision decision, String content, List<ConditionNode> candidates,
                       boolean inclusive, AiRoutingConfig config) {
        try {
            String json = extractJson(content);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            List<String> rawSelections = extractSelections(map.get("nodeKeys"));
            if (rawSelections.isEmpty()) {
                // 兼容只返回单个 nodeKey / branch 字段的模型输出
                rawSelections = extractSelections(map.getOrDefault("nodeKey", map.get("branch")));
            }
            decision.setRawSelections(rawSelections);
            decision.setConfidence(parseConfidence(map.get("confidence")));
            decision.setReasoning(map.get("reasoning") instanceof String r ? r : null);

            List<String> matched = new ArrayList<>();
            for (String selection : rawSelections) {
                String token = selection == null ? "" : selection.trim();
                if (token.isEmpty()) {
                    continue;
                }
                candidates.stream()
                        .filter(c -> token.equals(c.getNodeKey())
                                || token.equals(StrUtil.trimToEmpty(c.getNodeName())))
                        .map(ConditionNode::getNodeKey)
                        .filter(k -> !matched.contains(k))
                        .findFirst()
                        .ifPresent(matched::add);
                if (!inclusive && !matched.isEmpty()) {
                    break;
                }
            }

            decision.setMatchedNodeKeys(matched);
            if (matched.isEmpty()) {
                decision.setStatus("FALLBACK");
                if (decision.getReasoning() == null) {
                    decision.setReasoning("AI返回的分支未匹配任何候选");
                }
            } else if (decision.getConfidence() == null
                    || decision.getConfidence() < config.getConfidenceThresholdOrDefault()) {
                decision.setStatus("LOW_CONFIDENCE");
                decision.setMatchedNodeKeys(Collections.emptyList());
            } else {
                decision.setStatus("SUCCESS");
            }
        } catch (Exception e) {
            log.warn("解析AI路由响应失败: {}", e.getMessage());
            decision.setStatus("FALLBACK");
            decision.setReasoning("AI响应解析失败：" + e.getMessage());
            decision.setMatchedNodeKeys(Collections.emptyList());
        }
    }

    /**
     * 从响应字段提取分支标识列表（兼容数组、单个字符串）
     */
    private List<String> extractSelections(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.toList());
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? Collections.emptyList() : List.of(s);
    }

    private void saveRecord(NodeModel nodeModel, List<ConditionNode> candidates,
                            RoutingDecision decision, Long instanceId) {
        try {
            WfAiRouteRecord record = new WfAiRouteRecord();
            record.setProcessInstanceId(instanceId);
            record.setGatewayNodeKey(nodeModel.getNodeKey());
            record.setGatewayNodeName(nodeModel.getNodeName());
            record.setGatewayType(nodeModel.getType());
            record.setCandidateBranches(objectMapper.writeValueAsString(candidates.stream()
                    .map(c -> Map.of("nodeKey", StrUtil.blankToDefault(c.getNodeKey(), ""),
                            "nodeName", StrUtil.blankToDefault(c.getNodeName(), "")))
                    .collect(Collectors.toList())));
            // 审计保留 AI 的原始意图（未匹配候选时 selected 为匹配结果为空，raw 才是模型原话）
            record.setSelectedBranches(decision.getMatchedNodeKeys() != null && !decision.getMatchedNodeKeys().isEmpty()
                    ? String.join(",", decision.getMatchedNodeKeys()) : null);
            record.setConfidence(decision.getConfidence());
            record.setReasoning(decision.getReasoning());
            record.setRawResponse(decision.getRawResponse());
            record.setStatus(decision.getStatus());
            record.setProvider(decision.getProvider());
            record.setModelName(decision.getModelName());
            record.setCreateTime(LocalDateTime.now());
            routeRecordMapper.insert(record);
        } catch (Exception e) {
            // 审计失败不影响路由本身
            log.error("AI路由审计落库失败: nodeKey={}", nodeModel.getNodeKey(), e);
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private Integer parseConfidence(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isSensitiveField(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || lowerKey.contains("secret")
                || lowerKey.contains("token") || lowerKey.contains("key");
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return (String) value;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}
