package com.forge.modules.workflow.framework.title;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程标题工具：按模型标题规则（${} 模板）在发起时渲染标题，并从实例变量 JSON 提取标题。
 * <p>
 * 模板占位符 ${xxx}：内置 processName 流程名称 / initiatorName 发起人姓名 / businessKey 业务标识 /
 * date 当日(yyyy-MM-dd)，其余按表单变量名取值；变量缺失替换为空串；
 * 渲染结果去首尾空白后为空视为无标题（返回 null，展示侧回退流程名称）。
 * 前端 src/utils/titleRule.ts 同构实现（发起弹窗实时预览），两侧语法保持一致。
 *
 * @author forge-admin
 */
@Slf4j
public final class InstanceTitleRenderer {

    /**
     * 标题在实例变量中的键（双下划线前缀=系统保留，避免与表单字段冲突）
     */
    public static final String TITLE_VARIABLE = "__title";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InstanceTitleRenderer() {
    }

    /**
     * 渲染标题。规则为空或渲染结果为空白返回 null（不生成标题）。
     *
     * @param rule          标题规则模板
     * @param processName   流程名称（内置变量）
     * @param initiatorName 发起人姓名（内置变量）
     * @param businessKey   业务标识（内置变量）
     * @param variables     发起时流程变量（表单字段取值来源，可为 null）
     */
    public static String render(String rule, String processName, String initiatorName,
                                String businessKey, Map<String, Object> variables) {
        if (rule == null || rule.isBlank()) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(rule);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolveVariable(
                    matcher.group(1).trim(), processName, initiatorName, businessKey, variables)));
        }
        matcher.appendTail(result);
        String title = result.toString().trim();
        return title.isEmpty() ? null : title;
    }

    private static String resolveVariable(String name, String processName, String initiatorName,
                                          String businessKey, Map<String, Object> variables) {
        return switch (name) {
            case "processName" -> nullToEmpty(processName);
            case "initiatorName" -> nullToEmpty(initiatorName);
            case "businessKey" -> nullToEmpty(businessKey);
            case "date" -> LocalDate.now().format(DATE_FORMATTER);
            default -> {
                if (variables == null) {
                    yield "";
                }
                Object value = variables.get(name);
                yield value == null ? "" : String.valueOf(value);
            }
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 从实例 variable JSON 提取标题（读取管道统一入口：实例列表 UNION 行/活跃历史转换/任务批量）
     *
     * @param variableJson flw_instance.variable / flw_his_instance.variable 的 JSON 字符串，可为 null
     * @return 标题，不存在或解析失败返回 null
     */
    public static String extractTitle(String variableJson) {
        if (variableJson == null || variableJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(variableJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object title = map.get(TITLE_VARIABLE);
            return title == null ? null : String.valueOf(title);
        } catch (Exception e) {
            log.warn("实例变量 JSON 解析失败，忽略标题提取: {}", e.getMessage());
            return null;
        }
    }
}
