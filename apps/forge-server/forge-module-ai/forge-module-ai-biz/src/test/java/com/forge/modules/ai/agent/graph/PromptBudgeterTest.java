package com.forge.modules.ai.agent.graph;

import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.entity.AiSqlWhitelist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词预算器单测：历史分级渲染/预算折叠、汇报预算、表相关性选择、记忆截断（AC1）
 */
class PromptBudgeterTest {

    private AgentProperties properties;
    private PromptBudgeter budgeter;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        budgeter = new PromptBudgeter(properties);
    }

    // ========== 工具历史 ==========

    @Test
    void 空历史返回空串() {
        assertEquals("", budgeter.renderToolHistory(List.of(), null));
        assertEquals("", budgeter.renderToolHistory(null, null));
    }

    @Test
    void 最近记录详尽_较早记录简略() {
        AgentProperties.Prompt p = properties.getPrompt();
        p.setHistoryRecentCount(1);
        p.setHistoryRecentLimit(2000);
        p.setHistoryOldLimit(50);

        List<Object> history = new ArrayList<>();
        history.add(record("http_fetch", Map.of("url", "a"), "x".repeat(300), "SUCCESS"));
        history.add(record("sql_query", Map.of("sql", "s"), "y".repeat(300), "SUCCESS"));

        String rendered = budgeter.renderToolHistory(history, null);
        // 旧记录被压到 oldLimit+后缀以内，新记录保留 300 字符完整结果
        assertTrue(rendered.contains("x".repeat(50)));
        assertFalse(rendered.contains("x".repeat(51)));
        assertTrue(rendered.contains("y".repeat(300)));
    }

    @Test
    void 超总预算_最旧记录折叠为单行() {
        AgentProperties.Prompt p = properties.getPrompt();
        p.setHistoryBudget(300);
        p.setHistoryRecentCount(1);
        p.setHistoryRecentLimit(150);

        List<Object> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            history.add(record("tool_" + i, Map.of(), "结果".repeat(50) + i, "SUCCESS"));
        }
        String rendered = budgeter.renderToolHistory(history, null);
        // 折叠从最旧开始，最新的完整保留
        assertTrue(rendered.contains("结果".repeat(50) + "4"));
        assertTrue(rendered.contains("已执行"));
        assertFalse(rendered.contains("结果".repeat(50) + "0"));
    }

    @Test
    void 失败记录渲染错误信息_按专家过滤() {
        Map<String, Object> failed = record("sql_query", Map.of(), null, "FAILED");
        failed.put("error", "表不在白名单");
        failed.put("agentCode", "expert-a");
        Map<String, Object> other = record("http_fetch", Map.of(), "ok", "SUCCESS");
        other.put("agentCode", "expert-b");

        String forA = budgeter.renderToolHistory(List.of(failed, other), "expert-a");
        assertTrue(forA.contains("失败: 表不在白名单"));
        assertFalse(forA.contains("http_fetch"));

        String all = budgeter.renderToolHistory(List.of(failed, other), null);
        assertTrue(all.contains("http_fetch"));
    }

    // ========== 专家汇报 ==========

    @Test
    void 汇报单条截断_超总预算压旧() {
        AgentProperties.Prompt p = properties.getPrompt();
        p.setReportsPerLimit(100);
        p.setReportsBudget(250);

        String rendered = budgeter.renderReports(List.of(
                "旧汇报".repeat(80), "新汇报".repeat(20)));
        assertTrue(rendered.contains("新汇报".repeat(20)));
        // 旧汇报先被单条截断再被压缩
        assertFalse(rendered.contains("旧汇报".repeat(80)));
    }

    @Test
    void 空汇报返回空串() {
        assertEquals("", budgeter.renderReports(List.of()));
    }

    // ========== 表清单相关性 ==========

    @Test
    void 相关表优先_超出上限标注省略() {
        AgentProperties.Prompt p = properties.getPrompt();
        p.setMaxTables(2);

        List<AiSqlWhitelist> tables = List.of(
                table("sys_user", "用户表"),
                table("ai_document", "文档表"),
                table("ai_message", "消息表"),
                table("sys_dept", "部门表"));
        String rendered = budgeter.renderTables("查询用户表里的数据分布", tables);
        assertTrue(rendered.contains("sys_user"));
        assertTrue(rendered.contains("另有 2 张表未列出"));
        // 无关表按表名序补位
        assertTrue(rendered.contains("ai_document"));
    }

    @Test
    void 无匹配时按表名补齐() {
        AgentProperties.Prompt p = properties.getPrompt();
        p.setMaxTables(2);
        String rendered = budgeter.renderTables("完全不相关的输入xyz",
                List.of(table("sys_user", "用户表"), table("ai_document", "文档表"), table("sys_dept", "部门表")));
        assertTrue(rendered.contains("另有 1 张表未列出"));
        assertTrue(rendered.contains("sys_dept"));
    }

    @Test
    void 表数不超上限全部列出() {
        String rendered = budgeter.renderTables("任意",
                List.of(table("sys_user", "用户表"), table("sys_dept", "部门表")));
        assertFalse(rendered.contains("未列出"));
    }

    @Test
    void 列名注入_探查失败降级为仅表名() {
        String rendered = budgeter.renderTables("会话统计",
                List.of(table("ai_chat_conversation", "会话表")),
                t -> List.of("id", "title", "create_time", "update_time", "deleted"));
        assertTrue(rendered.contains("- ai_chat_conversation（会话表）: id, title, create_time, update_time, deleted"));

        // 探查失败（空列）→ 仅表名，不输出冒号段
        String degraded = budgeter.renderTables("会话统计",
                List.of(table("ai_chat_conversation", "会话表")), t -> List.of());
        assertTrue(degraded.contains("ai_chat_conversation"));
        assertFalse(degraded.contains(": id"));

        // 无 provider（旧行为）等价降级
        String legacy = budgeter.renderTables("会话统计",
                List.of(table("ai_chat_conversation", "会话表")));
        assertEquals(degraded, legacy);
    }

    @Test
    void 单表列数超上限截断标注() {
        AgentProperties.Prompt p = properties.getPrompt();
        p.setTableColumnsPerTable(3);
        String rendered = budgeter.renderTables("会话统计",
                List.of(table("t", null)),
                t -> List.of("a", "b", "c", "d", "e"));
        assertTrue(rendered.contains("a, b, c 等 5 列"));
        assertFalse(rendered.contains(", d"));
    }

    // ========== 记忆 ==========

    @Test
    void 记忆超预算截断() {
        AgentProperties.Prompt p = properties.getPrompt();
        p.setMemoryBudget(100);
        String rendered = budgeter.renderMemory("长".repeat(500));
        assertTrue(rendered.length() <= 120);
        assertTrue(rendered.contains("截断"));
    }

    @Test
    void 空记忆返回空串() {
        assertEquals("", budgeter.renderMemory(null));
        assertEquals("", budgeter.renderMemory("  "));
    }

    // ========== 工具 ==========

    private Map<String, Object> record(String tool, Map<String, Object> args, String result, String status) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("tool", tool);
        record.put("args", args);
        if (result != null) {
            record.put("result", result);
        }
        record.put("status", status);
        return record;
    }

    private AiSqlWhitelist table(String name, String comment) {
        AiSqlWhitelist table = new AiSqlWhitelist();
        table.setTableName(name);
        table.setTableComment(comment);
        return table;
    }
}
