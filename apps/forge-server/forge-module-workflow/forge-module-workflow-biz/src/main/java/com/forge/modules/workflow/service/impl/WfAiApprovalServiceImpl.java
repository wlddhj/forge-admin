package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.client.PythonAiClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.workflow.dto.ai.AiApprovalConfig;
import com.forge.modules.workflow.dto.ai.AiApprovalResult;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.Decision;
import com.forge.modules.workflow.dto.ai.AiApprovalResult.AiStatus;
import com.forge.modules.workflow.service.WfAiApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI审批服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfAiApprovalServiceImpl implements WfAiApprovalService {

    private final PythonAiClient pythonAiClient;
    private final ObjectMapper objectMapper;

    @Override
    public AiApprovalResult executeAiApproval(AiApprovalConfig config,
                                               String processInstanceId,
                                               String taskId,
                                               String taskName,
                                               Map<String, Object> variables) {
        AiApprovalResult result = new AiApprovalResult();

        try {
            // 1. 构建审批提示词
            String prompt = buildApprovalPrompt(config, taskName, variables);

            // 2. 调用AI服务
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setModelName(config.getModelName());
            chatRequest.setContent(prompt);
            chatRequest.setTemperature(0.3); // 较低温度确保稳定输出

            // 构建系统消息
            ChatRequest.MessageItem systemMsg = new ChatRequest.MessageItem();
            systemMsg.setRole("system");
            systemMsg.setContent(buildSystemPrompt());
            chatRequest.setMessages(List.of(systemMsg));

            // 3. 执行AI调用
            ChatResponse chatResponse = pythonAiClient.chat(chatRequest);

            // 4. 解析AI响应
            if (chatResponse != null && Boolean.TRUE.equals(chatResponse.getSuccess())) {
                result.setRawResponse(chatResponse.getContent());
                parseAiDecision(result, chatResponse.getContent(), config);
            } else {
                result.setStatus(AiStatus.ERROR);
                result.setDecision(Decision.MANUAL);
                applyFallback(result, config);
            }

        } catch (Exception e) {
            log.error("AI审批执行失败: processInstanceId={}, taskId={}, error={}",
                    processInstanceId, taskId, e.getMessage(), e);
            result.setStatus(AiStatus.ERROR);
            result.setDecision(Decision.MANUAL);
            applyFallback(result, config);
        }

        return result;
    }

    @Override
    public String buildApprovalPrompt(AiApprovalConfig config, String taskName, Map<String, Object> variables) {
        StringBuilder prompt = new StringBuilder();

        // 如果有自定义提示词，优先使用
        if (StrUtil.isNotBlank(config.getCustomPrompt())) {
            prompt.append(config.getCustomPrompt()).append("\n\n");
        }

        // 添加任务信息
        prompt.append("当前审批节点：").append(taskName).append("\n");

        // 添加流程变量摘要
        prompt.append("流程数据摘要：\n");
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                // 过滤敏感字段
                if (!isSensitiveField(key)) {
                    prompt.append("- ").append(key).append(": ").append(formatValue(value)).append("\n");
                }
            }
        }

        prompt.append("\n请根据以上信息判断是否应该通过审批，并给出置信度（0-100）。");

        return prompt.toString();
    }

    @Override
    public AiApprovalConfig parseAiConfig(Map<String, Object> extendConfig) {
        if (extendConfig == null) {
            return null;
        }

        Object aiConfigObj = extendConfig.get("aiApproval");
        if (aiConfigObj == null) {
            return null;
        }

        try {
            if (aiConfigObj instanceof Map) {
                return objectMapper.convertValue(aiConfigObj, AiApprovalConfig.class);
            } else if (aiConfigObj instanceof String) {
                return objectMapper.readValue((String) aiConfigObj, AiApprovalConfig.class);
            }
        } catch (Exception e) {
            log.warn("解析AI审批配置失败: {}", e.getMessage());
        }

        return null;
    }

    // ========== 私有方法 ==========

    private String buildSystemPrompt() {
        return "你是一个工作流审批助手。请根据提供的审批信息判断是否应该通过审批。\n" +
               "你必须以JSON格式返回结果，格式如下：\n" +
               "{\"decision\": \"APPROVE\" 或 \"REJECT\", \"confidence\": 0-100, \"reasoning\": \"审批理由说明\"}\n" +
               "决策规则：\n" +
               "- APPROVE: 认为应该通过审批\n" +
               "- REJECT: 认为应该驳回审批\n" +
               "- 请给出具体的审批理由";
    }

    private void parseAiDecision(AiApprovalResult result, String content, AiApprovalConfig config) {
        try {
            // 提取JSON部分
            String jsonContent = extractJson(content);
            Map<String, Object> decisionMap = objectMapper.readValue(jsonContent, Map.class);

            String decisionStr = (String) decisionMap.get("decision");
            Integer confidence = parseConfidence(decisionMap.get("confidence"));
            String reasoning = (String) decisionMap.get("reasoning");

            result.setConfidence(confidence);
            result.setReasoning(reasoning);

            // 判断置信度
            if (confidence == null || confidence < config.getConfidenceThreshold()) {
                result.setStatus(AiStatus.LOW_CONFIDENCE);
                result.setDecision(Decision.MANUAL);
                applyFallback(result, config);
            } else {
                result.setStatus(AiStatus.SUCCESS);
                if ("APPROVE".equalsIgnoreCase(decisionStr)) {
                    result.setDecision(Decision.APPROVE);
                } else if ("REJECT".equalsIgnoreCase(decisionStr)) {
                    result.setDecision(Decision.REJECT);
                } else {
                    result.setDecision(Decision.MANUAL);
                }
            }

        } catch (Exception e) {
            log.warn("解析AI决策响应失败: {}", e.getMessage());
            result.setStatus(AiStatus.ERROR);
            result.setDecision(Decision.MANUAL);
            applyFallback(result, config);
        }
    }

    private String extractJson(String content) {
        // 从内容中提取JSON部分
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private Integer parseConfidence(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyFallback(AiApprovalResult result, AiApprovalConfig config) {
        String strategy = config.getFallbackStrategy();
        if ("DEFAULT_PASS".equalsIgnoreCase(strategy)) {
            result.setDecision(Decision.APPROVE);
            String reasoning = result.getReasoning();
            result.setReasoning(reasoning != null ? reasoning + " [回退策略：默认通过]" : "回退策略：默认通过");
        } else if ("DEFAULT_REJECT".equalsIgnoreCase(strategy)) {
            result.setDecision(Decision.REJECT);
            String reasoning = result.getReasoning();
            result.setReasoning(reasoning != null ? reasoning + " [回退策略：默认驳回]" : "回退策略：默认驳回");
        } else {
            result.setDecision(Decision.MANUAL);
            String reasoning = result.getReasoning();
            result.setReasoning(reasoning != null ? reasoning + " [需要人工审批]" : "需要人工审批");
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