package com.forge.modules.contract.framework.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 合同模板 HTML 管道：sanitize（受控白名单）→ 芯片填充 → 变量提取。
 * 编辑器受控样式与白名单一一对应，超出白名单的标签/属性在保存与渲染时统一剥除。
 *
 * @author forge-admin
 */
public final class ContractHtmlProcessor {

    private static final String FIELD_ATTR = "data-field";

    /** 受控标签白名单（与前端 tiptap 编辑器可用扩展一一对应） */
    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "div", "h1", "h2", "h3", "h4",
                    "ul", "ol", "li",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td",
                    "strong", "em", "u", "s", "br", "sub", "sup", "blockquote", "mark",
                    "span", "a", "img")
            // 变量芯片：data-field 是渲染键，class 用于芯片样式
            .addAttributes("span", FIELD_ATTR, "class")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "width", "height")
            // 对齐等有限排版经 style 携带（tiptap 受控产出；PDF 渲染面无执行环境）
            .addAttributes(":all", "style");

    private ContractHtmlProcessor() {
    }

    /**
     * 白名单清洗，返回 body 内层 HTML
     */
    public static String sanitize(String html) {
        String clean = Jsoup.clean(html, "", SAFELIST);
        Document doc = Jsoup.parseBodyFragment(clean);
        return doc.body().html();
    }

    /**
     * 填充变量芯片：有值则替换芯片文本（jsoup text() 自动转义防注入），缺值保留原标签文本
     */
    public static String fillChips(String html, Map<String, Object> values) {
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element el : doc.body().select("[" + FIELD_ATTR + "]")) {
            String key = el.attr(FIELD_ATTR);
            if (values != null && values.containsKey(key) && values.get(key) != null) {
                el.text(String.valueOf(values.get(key)));
            }
        }
        return doc.body().html();
    }

    /**
     * 提取模板内变量 key（文档序去重）
     */
    public static Set<String> extractFieldKeys(String html) {
        Set<String> keys = new LinkedHashSet<>();
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element el : doc.select("[" + FIELD_ATTR + "]")) {
            String key = el.attr(FIELD_ATTR).trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }
}
