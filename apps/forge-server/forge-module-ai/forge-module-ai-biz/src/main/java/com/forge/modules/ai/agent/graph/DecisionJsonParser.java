package com.forge.modules.ai.agent.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策 JSON 解析：从 LLM 输出中提取平衡 JSON 块并解析为决策对象（单 Agent 图与团队协作图共用）
 */
@Component
@RequiredArgsConstructor
public class DecisionJsonParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析决策 JSON：{"action":"tool","calls":[...],"thought"} | {"action":"final","answer"}
     * 兼容单工具格式 {"action":"tool","tool":"x","args":{...}} 与多个独立 JSON 块合并
     */
    public Map<String, Object> parseDecision(String content) {
        if (content == null) {
            return null;
        }
        List<JsonNode> nodes = extractJsonObjects(content.trim());
        if (nodes.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> calls = new ArrayList<>();
        String finalAnswer = null;
        String thought = "";
        for (JsonNode node : nodes) {
            String action = node.path("action").asText("");
            if (node.path("calls").isArray()) {
                for (JsonNode call : node.path("calls")) {
                    if (call.path("tool").asText("").isBlank()) {
                        continue;
                    }
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("tool", call.path("tool").asText());
                    c.put("args", objectMapper.convertValue(call.path("args"), Map.class));
                    calls.add(c);
                }
            }
            if ("tool".equals(action) && !node.path("tool").asText("").isBlank()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("tool", node.path("tool").asText());
                c.put("args", objectMapper.convertValue(node.path("args"), Map.class));
                calls.add(c);
            }
            if ("final".equals(action)) {
                finalAnswer = node.path("answer").asText("");
            }
            if (thought.isEmpty()) {
                thought = node.path("thought").asText("");
            }
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        if (!calls.isEmpty()) {
            decision.put("action", "tool");
            decision.put("calls", calls);
            decision.put("thought", thought);
            return decision;
        }
        if (finalAnswer != null) {
            decision.put("action", "final");
            decision.put("answer", finalAnswer);
            return decision;
        }
        return null;
    }

    /**
     * 扫描文本中所有平衡的大括号块并尝试解析为 JSON（忽略非法块）
     */
    public List<JsonNode> extractJsonObjects(String text) {
        List<JsonNode> nodes = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        try {
                            nodes.add(objectMapper.readTree(text.substring(start, i + 1)));
                        } catch (Exception ignored) {
                            // 非法块跳过
                        }
                        start = -1;
                    }
                }
            }
        }
        return nodes;
    }

    /**
     * 截断超长文本（事件载荷与落库输出上限 3000 字符）
     */
    public String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 3000 ? text.substring(0, 3000) + "…(截断)" : text;
    }
}
