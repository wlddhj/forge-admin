package com.forge.modules.workflow.framework.title;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 流程标题渲染/提取规则单测
 */
class InstanceTitleRendererTest {

    private final Map<String, Object> variables = new HashMap<>(Map.of(
            "projectName", "智慧园区",
            "days", 3
    ));

    @Test
    @DisplayName("内置变量 + 表单变量混排渲染")
    void rendersBuiltinsAndFormVariables() {
        String title = InstanceTitleRenderer.render("${initiatorName}的${processName}：${projectName}",
                "请假申请", "张三", null, variables);
        assertEquals("张三的请假申请：智慧园区", title);
    }

    @Test
    @DisplayName("businessKey 与数值型表单变量")
    void rendersBusinessKeyAndNumericVariable() {
        String title = InstanceTitleRenderer.render("${businessKey}请假${days}天",
                "请假", "张三", "leave:1", variables);
        assertEquals("leave:1请假3天", title);
    }

    @Test
    @DisplayName("date 内置变量按 yyyy-MM-dd 渲染")
    void rendersDateBuiltin() {
        String title = InstanceTitleRenderer.render("${date}提交", "p", "u", null, null);
        assertEquals(java.time.LocalDate.now().toString() + "提交", title);
    }

    @Test
    @DisplayName("缺失变量替换为空串，不抛错")
    void missingVariableRendersEmpty() {
        String title = InstanceTitleRenderer.render("[${notExist}]${processName}",
                "报销", "李四", null, variables);
        assertEquals("[]报销", title);
    }

    @Test
    @DisplayName("渲染结果为空 → null（回退流程名称）")
    void blankResultReturnsNull() {
        assertNull(InstanceTitleRenderer.render("${notExist}", "p", "u", null, variables));
        assertNull(InstanceTitleRenderer.render("   ", "p", "u", null, variables));
    }

    @Test
    @DisplayName("规则为空 → null 不生成")
    void blankRuleReturnsNull() {
        assertNull(InstanceTitleRenderer.render(null, "p", "u", null, variables));
        assertNull(InstanceTitleRenderer.render("", "p", "u", null, variables));
    }

    @Test
    @DisplayName("变量 JSON 提取 __title")
    void extractsTitleFromVariableJson() {
        assertEquals("张三的请假", InstanceTitleRenderer.extractTitle(
                "{\"initiator\":\"1\",\"__title\":\"张三的请假\"}"));
        assertNull(InstanceTitleRenderer.extractTitle("{\"initiator\":\"1\"}"));
        assertNull(InstanceTitleRenderer.extractTitle(null));
        assertNull(InstanceTitleRenderer.extractTitle("not-json"));
    }

    @Test
    @DisplayName("占位符含正则特殊字符不干扰渲染")
    void regexSafeReplacement() {
        String title = InstanceTitleRenderer.render("${projectName}$100",
                "p", "u", null, Map.of("projectName", "A$B"));
        assertEquals("A$B$100", title);
    }
}
