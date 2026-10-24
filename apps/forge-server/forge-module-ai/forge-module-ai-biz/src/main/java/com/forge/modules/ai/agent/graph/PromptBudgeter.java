package com.forge.modules.ai.agent.graph;

import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.entity.AiSqlWhitelist;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 决策提示词预算器：工具历史分级渲染（近详远简+超预算折叠）、专家汇报预算、
 * 表清单按任务相关性选择、多轮记忆截断——消除工具循环内 O(N²) 的上下文膨胀。
 */
@Component
public class PromptBudgeter {

    private final AgentProperties properties;

    public PromptBudgeter(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 渲染工具历史：最近 N 条保留较完整结果，更早的只留一句话；总预算超限时旧记录折叠为单行。
     * 记录结构：{tool, args, result|error, status[, agentCode]}（agentCode 非空时仅渲染该专家的记录）
     */
    public String renderToolHistory(List<?> history, String agentCode) {
        List<Map<?, ?>> records = filterRecords(history, agentCode);
        if (records.isEmpty()) {
            return "";
        }
        AgentProperties.Prompt p = properties.getPrompt();
        List<String> lines = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            Map<?, ?> record = records.get(i);
            boolean recent = i >= records.size() - Math.max(1, p.getHistoryRecentCount());
            int limit = recent ? p.getHistoryRecentLimit() : p.getHistoryOldLimit();
            lines.add(renderRecord(record, limit));
        }
        // 总预算超限：从最旧开始折叠为「工具名+状态」单行，直到进入预算
        int over = totalLength(lines) - p.getHistoryBudget();
        int foldIndex = 0;
        while (over > 0 && foldIndex < lines.size() - 1) {
            String full = lines.get(foldIndex);
            String folded = foldLine(records.get(foldIndex));
            over -= full.length() - folded.length();
            lines.set(foldIndex, folded);
            foldIndex++;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
        return sb.toString();
    }

    /** 渲染专家汇报：单条截断 + 总预算（超限时最旧的先压到 200 字符） */
    public String renderReports(List<?> reports) {
        if (reports == null || reports.isEmpty()) {
            return "";
        }
        AgentProperties.Prompt p = properties.getPrompt();
        List<String> lines = new ArrayList<>(reports.size());
        for (Object report : reports) {
            lines.add("- " + truncate(String.valueOf(report), p.getReportsPerLimit()) + "\n");
        }
        int over = totalLength(lines) - p.getReportsBudget();
        int i = 0;
        while (over > 0 && i < lines.size() - 1) {
            String full = lines.get(i);
            String squeezed = truncate(full, 200);
            over -= full.length() - squeezed.length();
            lines.set(i, squeezed);
            i++;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * 表清单按任务相关性选择：输入分词与表名/注释 token 重叠数降序，同分按表名序；
     * 命中表优先，不足 maxTables 时按表名补齐；超出部分标注省略数。
     */
    public String renderTables(String input, List<AiSqlWhitelist> tables) {
        if (tables == null || tables.isEmpty()) {
            return "";
        }
        AgentProperties.Prompt p = properties.getPrompt();
        Set<String> tokens = tokenize(input);
        List<AiSqlWhitelist> matched = new ArrayList<>();
        List<AiSqlWhitelist> rest = new ArrayList<>(tables);
        if (!tokens.isEmpty()) {
            rest.sort(Comparator.comparing(AiSqlWhitelist::getTableName));
            for (AiSqlWhitelist table : rest) {
                if (relevance(table, tokens) > 0) {
                    matched.add(table);
                }
            }
            rest.removeAll(matched);
        }
        List<AiSqlWhitelist> selected = new ArrayList<>(matched);
        if (selected.size() < p.getMaxTables()) {
            selected.addAll(rest.subList(0, Math.min(rest.size(), p.getMaxTables() - selected.size())));
        } else {
            selected = selected.subList(0, p.getMaxTables());
        }
        int omitted = tables.size() - selected.size();
        StringBuilder sb = new StringBuilder();
        for (AiSqlWhitelist table : selected) {
            sb.append("- ").append(table.getTableName());
            if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
                sb.append("（").append(table.getTableComment()).append("）");
            }
            sb.append('\n');
        }
        if (omitted > 0) {
            sb.append("（另有 ").append(omitted).append(" 张表未列出，如需使用请在决策中说明）\n");
        }
        return sb.toString();
    }

    /** 多轮记忆段截断 */
    public String renderMemory(String memory) {
        if (memory == null || memory.isBlank()) {
            return "";
        }
        return truncate(memory.trim(), properties.getPrompt().getMemoryBudget());
    }

    // ========== 内部 ==========

    private List<Map<?, ?>> filterRecords(List<?> history, String agentCode) {
        List<Map<?, ?>> records = new ArrayList<>();
        if (history == null) {
            return records;
        }
        for (Object h : history) {
            if (h instanceof Map<?, ?> record
                    && (agentCode == null || agentCode.equals(record.get("agentCode")))) {
                records.add(record);
            }
        }
        return records;
    }

    private String renderRecord(Map<?, ?> record, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.get("tool")).append("(").append(record.get("args")).append(")");
        Object error = record.get("error");
        if (error != null) {
            sb.append(" → 失败: ").append(truncate(String.valueOf(error), limit));
            return sb.toString();
        }
        Object result = record.get("result");
        sb.append(" → ").append(truncate(String.valueOf(result), limit));
        return sb.toString();
    }

    private String foldLine(Map<?, ?> record) {
        return record.get("tool") + "(" + record.get("args") + ") → 已执行"
                + ("FAILED".equals(String.valueOf(record.get("status"))) ? "（失败）" : "（结果已折叠）");
    }

    /** 输入分词：中文按 2 字滑窗、英文/数字按词，过滤长度 <2 的碎片 */
    private Set<String> tokenize(String input) {
        Set<String> tokens = new LinkedHashSet<>();
        if (input == null || input.isBlank()) {
            return tokens;
        }
        for (String word : input.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 2) {
                if (word.matches("\\p{Script=Han}+")) {
                    for (int i = 0; i < word.length() - 1; i++) {
                        tokens.add(word.substring(i, i + 2));
                    }
                } else {
                    tokens.add(word);
                }
            }
        }
        return tokens;
    }

    private int relevance(AiSqlWhitelist table, Set<String> tokens) {
        String name = table.getTableName() == null ? "" : table.getTableName().toLowerCase();
        String comment = table.getTableComment() == null ? "" : table.getTableComment().toLowerCase();
        int score = 0;
        for (String token : tokens) {
            if (name.contains(token) || token.contains(name.replace('_', ' '))) {
                score += 2;
            }
            if (comment.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private int totalLength(List<String> lines) {
        int total = 0;
        for (String line : lines) {
            total += line.length();
        }
        return total;
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        return text.length() > limit ? text.substring(0, limit) + "…(截断)" : text;
    }
}
