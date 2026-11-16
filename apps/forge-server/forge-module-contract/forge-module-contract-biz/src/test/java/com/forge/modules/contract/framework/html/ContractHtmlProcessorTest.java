package com.forge.modules.contract.framework.html;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTML 管道单测：sanitize 白名单 / 芯片填充转义 / 变量提取
 *
 * @author forge-admin
 */
class ContractHtmlProcessorTest {

    @Test
    void sanitizeStripsDangerousTagsAndKeepsWhitelist() {
        String html = "<h1>标题</h1><p onclick=\"x()\">段落</p><script>alert(1)</script>"
                + "<table><tr><th>表头</th></tr></table>"
                + "<span data-field=\"k1\" class=\"ct-var\">变量</span>"
                + "<div><font color=\"red\">自定义字体应被剥离</font>文本保留</div>";
        String clean = ContractHtmlProcessor.sanitize(html);
        assertFalse(clean.contains("script"));
        assertFalse(clean.contains("onclick"));
        assertFalse(clean.contains("<font"));
        assertTrue(clean.contains("<h1>"));
        assertTrue(clean.contains("data-field=\"k1\""));
        assertTrue(clean.contains("文本保留"));
    }

    @Test
    void fillChipsReplacesAndEscapes() {
        String html = "<p>甲方：<span data-field=\"party_a\">甲方名称</span></p>"
                + "<p>备注：<span data-field=\"remark\">备注</span></p>";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("party_a", "A<b>公司</b>&分公司");
        // remark 缺值 → 保留标签文本

        String filled = ContractHtmlProcessor.fillChips(html, values);
        assertTrue(filled.contains("A&lt;b&gt;公司&lt;/b&gt;&amp;分公司"), "值须转义防注入");
        assertTrue(filled.contains("<span data-field=\"remark\">备注</span>"), "缺值芯片保留原文本");
    }

    @Test
    void sanitizeKeepsTableColumnWidths() {
        String html = "<table><colgroup><col style=\"width: 30%\" /></colgroup>"
                + "<tbody><tr><td>甲</td></tr></tbody></table>";
        String clean = ContractHtmlProcessor.sanitize(html);
        assertTrue(clean.contains("colgroup"), "colgroup 须保留（可调列宽）");
        assertTrue(clean.contains("width: 30%"), "列宽样式须保留");
    }

    @Test
    void sanitizeKeepsHighlightAndFontSize() {
        String html = "<p><mark style=\"background-color: #ffff00\">重点条款</mark> "
                + "<span style=\"font-size: 18pt\">大字</span></p>";
        String clean = ContractHtmlProcessor.sanitize(html);
        assertTrue(clean.contains("<mark"), "mark(高亮)须保留");
        assertTrue(clean.contains("background-color"), "高亮色样式须保留");
        assertTrue(clean.contains("font-size"), "字号样式须保留");
    }

    @Test
    void extractFieldKeysDistinctInOrder() {
        String html = "<p><span data-field=\"b\">x</span> <span data-field=\"a\">y</span> "
                + "<span data-field=\"b\">z</span> <span data-field=\"\">空键忽略</span></p>";
        Set<String> keys = ContractHtmlProcessor.extractFieldKeys(html);
        assertEquals(2, keys.size());
        assertEquals("b", keys.iterator().next(), "文档序去重");
        assertTrue(keys.contains("a"));
    }
}
