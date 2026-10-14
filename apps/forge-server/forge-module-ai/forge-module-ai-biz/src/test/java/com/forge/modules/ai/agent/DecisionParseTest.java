package com.forge.modules.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 决策输出解析实证：复现 ConversationalAssistantGraph.extractJsonObjects/parseDecision 行为
 */
class DecisionParseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseRealModelOutput() throws Exception {
        String text = "1. 需要调用工具：{\"action\":\"tool\",\"calls\":[{\"tool\":\"sql_query\",\"args\":{\"sql\":\"SELECT COUNT(*) FROM sys_menu;\"},\"thought\":\"需要查询系统菜单数量。\"},{\"tool\":\"sql_query\",\"args\":{\"sql\":\"SELECT COUNT(*) FROM sys_role;\"},\"thought\":\"需要查询系统角色数量。\"}]}";

        List<JsonNode> nodes = extractJsonObjects(text);
        System.out.println("提取块数: " + nodes.size());
        assertFalse(nodes.isEmpty(), "应至少提取到一个 JSON 块");

        JsonNode node = nodes.get(0);
        assertEquals("tool", node.path("action").asText());
        assertEquals(2, node.path("calls").size());
        assertEquals("sql_query", node.path("calls").path(0).path("tool").asText());

        // convertValue 链路（parseDecision 内使用）
        Object args = objectMapper.convertValue(node.path("calls").path(0).path("args"), java.util.Map.class);
        assertNotNull(args);
        System.out.println("args=" + args);
    }

    @Test
    void parseMarkdownFencedOutput() {
        String text = "```json\n{\"action\":\"final\",\"answer\":\"系统中共有3个角色。\"}\n```";
        List<JsonNode> nodes = extractJsonObjects(text);
        assertEquals(1, nodes.size());
        assertEquals("final", nodes.get(0).path("action").asText());
    }

    @Test
    void parseTwoSeparateObjects() {
        String text = "需要调用工具：{\"action\":\"tool\",\"tool\":\"sql_query\",\"args\":{\"sql\":\"SELECT 1\"}} {\"action\":\"tool\",\"tool\":\"http_fetch\",\"args\":{\"url\":\"https://x\"}}";
        List<JsonNode> nodes = extractJsonObjects(text);
        assertEquals(2, nodes.size());
    }

    private List<JsonNode> extractJsonObjects(String text) {
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
                        } catch (Exception e) {
                            System.out.println("块解析失败: " + e);
                        }
                        start = -1;
                    }
                }
            }
        }
        return nodes;
    }
}
