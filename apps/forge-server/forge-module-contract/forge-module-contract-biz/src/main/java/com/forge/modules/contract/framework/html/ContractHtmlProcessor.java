package com.forge.modules.contract.framework.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;

import java.util.LinkedHashMap;
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
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td", "colgroup", "col",
                    "strong", "em", "u", "s", "br", "sub", "sup", "blockquote", "mark",
                    "span", "a", "img")
            // 变量芯片：data-field 是渲染键，class 用于芯片样式
            .addAttributes("span", FIELD_ATTR, "class")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "width", "height")
            // 可调列宽表格：colgroup/col 的列宽必须保留，否则 PDF 列宽塌陷
            .addAttributes("col", "width", "style")
            .addAttributes("th", "width")
            .addAttributes("td", "width")
            // 对齐等有限排版经 style 携带（tiptap 受控产出；PDF 渲染面无执行环境）
            .addAttributes(":all", "style");

    private ContractHtmlProcessor() {
    }

    /**
     * 白名单清洗，返回 body 内层 HTML。
     * 列表/表格单元格内的块级 p 解包为内联（引擎对块级子元素排版异常：列表标记独占行、
     * 单元格行高暴涨）；配合清除单元格空白文本节点与紧凑输出，杜绝 pre-wrap 把
     * pretty-print 换行渲染成真实空行（探针实证行距 116pt 的根因）。
     */
    public static String sanitize(String html) {
        String clean = Jsoup.clean(html, "", SAFELIST);
        Document doc = Jsoup.parseBodyFragment(clean);
        normalize(doc);
        return doc.body().html();
    }

    /** 渲染前归一化：td/th/li 的块级 p 解包（style 转移给宿主单元格）+ 空白清除 + 紧凑输出 */
    private static void normalize(Document doc) {
        doc.outputSettings().prettyPrint(false);
        doc.body().select("li > p, th > p, td > p").forEach(p -> {
            // 单元格对齐等样式写在 tiptap 的 td>p 上，解包前转移到 td/th/li 宿主（p 属性优先）
            if (p.hasAttr("style") && p.parent() != null) {
                mergeStyle(p.parent(), p.attr("style"));
            }
            p.unwrap();
        });
        for (Element cell : doc.body().select("td, th, li")) {
            for (TextNode tn : cell.textNodes()) {
                if (tn.isBlank()) {
                    tn.remove();
                }
            }
        }
    }

    /** 无序列表文本圆点 class（引擎不渲染原生 disc，渲染期注入真实"•"） */
    public static final String LI_MARK_CLASS = "ct-li-mark";

    /**
     * 填充变量芯片：有值则替换芯片文本（jsoup text() 自动转义防注入），缺值保留原标签文本。
     * 同时给无序列表项注入文本圆点——openhtmltopdf 不绘制 ul 原生 disc 标记（探针实证：
     * ol 数字可画、disc 恒缺失），注入真实"•"字符最确定；仅渲染期，存库内容保持干净。
     */
    public static String fillChips(String html, Map<String, Object> values) {
        Document doc = Jsoup.parseBodyFragment(html);
        normalize(doc);
        for (Element el : doc.body().select("[" + FIELD_ATTR + "]")) {
            String key = el.attr(FIELD_ATTR);
            if (values != null && values.containsKey(key) && values.get(key) != null) {
                el.text(String.valueOf(values.get(key)));
            }
        }
        for (Element li : doc.body().select("ul > li")) {
            Element first = li.firstElementChild();
            if (first == null || !first.hasClass(LI_MARK_CLASS)) {
                li.prependElement("span").addClass(LI_MARK_CLASS).text("\u2022");
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

    /** 合并内联 style 到宿主（source 属性优先覆盖宿主） */
    private static void mergeStyle(Element host, String sourceStyle) {
        Map<String, String> merged = parseStyle(host.attr("style"));
        merged.putAll(parseStyle(sourceStyle));
        StringBuilder sb = new StringBuilder();
        merged.forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(k).append(": ").append(v);
        });
        if (!sb.isEmpty()) {
            host.attr("style", sb.toString());
        }
    }

    private static Map<String, String> parseStyle(String style) {
        Map<String, String> map = new LinkedHashMap<>();
        if (style == null || style.isBlank()) {
            return map;
        }
        for (String pair : style.split(";")) {
            int idx = pair.indexOf(':');
            if (idx > 0 && idx < pair.length() - 1) {
                map.put(pair.substring(0, idx).trim(), pair.substring(idx + 1).trim());
            }
        }
        return map;
    }
}
