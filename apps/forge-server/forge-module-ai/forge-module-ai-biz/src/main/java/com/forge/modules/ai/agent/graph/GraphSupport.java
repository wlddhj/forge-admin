package com.forge.modules.ai.agent.graph;

import com.forge.modules.ai.agent.kernel.AgentGraphState;
import com.forge.common.exception.BusinessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 两张编排图（单 Agent / Supervisor 团队）的共享静态工具组。
 * 历史背景：四个方法曾在两图各持一份逐字副本，describePendingCalls 已开始漂移
 * （团队版丢失"决策信息缺失"分支）——收敛为单一实现（评审修复第四批 §4.2）。
 *
 * @author forge-admin
 */
final class GraphSupport {

    private GraphSupport() {
    }

    /**
     * 待执行高危工具明细（写入审批待办内容展示）：单一实现取单 Agent 版语义
     * （含"决策信息缺失"分支——团队版漂移已合并回此处）
     */
    static String describePendingCalls(AgentGraphState state, ObjectMapper objectMapper) {
        Object decisionObj = state.value(AgentGraphState.DECISION).orElse(null);
        if (!(decisionObj instanceof Map<?, ?> decision)) {
            return "（决策信息缺失）";
        }
        Object callsObj = decision.get("calls");
        if (!(callsObj instanceof List<?> calls) || calls.isEmpty()) {
            return "（无调用明细）";
        }
        StringBuilder sb = new StringBuilder();
        for (Object call : calls) {
            if (call instanceof Map<?, ?> c) {
                sb.append("工具: ").append(c.get("tool"));
                Object args = c.get("args");
                if (args != null) {
                    try {
                        sb.append("\n参数: ").append(objectMapper.writeValueAsString(args));
                    } catch (Exception ignored) {
                        sb.append("\n参数: ").append(args);
                    }
                }
                sb.append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * SSE 数据行 → 正文增量；[DONE]/坏帧返回 null；error 帧抛业务异常
     */
    static String extractContentDelta(String frame, ObjectMapper objectMapper) {
        if (frame == null || "[DONE]".equals(frame.trim())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(frame);
            if (node.has("error") && node.path("error").asBoolean(false)) {
                throw new BusinessException("流式总结失败: " + node.path("message").asText());
            }
            return node.path("content").asText("");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 总结节点超时（配置兜底 60s）
     */
    static long summaryTimeoutMs(Integer summaryTimeout) {
        return summaryTimeout == null || summaryTimeout <= 0 ? 60000L : summaryTimeout;
    }

    /**
     * 决策轮次等 Integer 状态值兜底
     */
    static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
