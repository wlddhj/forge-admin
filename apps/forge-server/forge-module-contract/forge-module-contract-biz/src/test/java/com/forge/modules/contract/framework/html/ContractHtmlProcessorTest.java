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
    void sanitizeKeepsCellParagraphWithAlign() {
        String html = "<table><tbody><tr>"
                + "<td><p style=\"text-align: center\">居中格</p></td>"
                + "<th><p style=\"text-align: right\">右表头</p></th>"
                + "</tr></tbody></table>";
        String clean = ContractHtmlProcessor.sanitize(html);
        // td>p 整体保留（tiptap schema 可回显），对齐随 p 存续
        assertTrue(clean.contains("text-align: center"), "td>p 对齐须保留: " + clean);
        assertTrue(clean.contains("text-align: right"), "th>p 对齐须保留");
        assertTrue(clean.contains("<td><p"), "td>p 结构保留（不解包）");
    }

    @Test
    void fillChipsInjectsBulletOnceAndIdempotent() {
        String html = "<ul><li>甲项</li><li>乙项</li></ul>";
        String once = ContractHtmlProcessor.fillChips(html, null);
        assertTrue(once.contains("ct-li-mark"), "ul 项应注入文本圆点");
        assertEquals(2, once.split("ct-li-mark", -1).length - 1, "每项一枚");
        String twice = ContractHtmlProcessor.fillChips(once, null);
        assertEquals(once, twice, "重复渲染不得叠加圆点");
        // 有序列表不注入
        String ol = ContractHtmlProcessor.fillChips("<ol><li>第一</li></ol>", null);
        org.junit.jupiter.api.Assertions.assertFalse(ol.contains("ct-li-mark"));
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
