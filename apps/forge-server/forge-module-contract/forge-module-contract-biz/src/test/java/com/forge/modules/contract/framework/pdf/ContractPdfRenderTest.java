package com.forge.modules.contract.framework.pdf;

import com.forge.modules.contract.enums.PaperSize;
import com.forge.modules.contract.framework.html.ContractHtmlProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 渲染/水印冒烟：验证思源可变 TTF 在 openhtmltopdf(PDFBox3) 下的中文渲染链路
 *
 * @author forge-admin
 */
class ContractPdfRenderTest {

    /** 首个含关键字的字形 y 坐标 */
    private float firstGlyphY(byte[] pdf, String key) {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            float[] y = { -1 };
            new org.apache.pdfbox.text.PDFTextStripper() {
                { setSortByPosition(true); }
                @Override protected void writeString(String t, java.util.List<org.apache.pdfbox.text.TextPosition> ps) {
                    for (var tp : ps) {
                        if (tp.getUnicode().contains(key) && y[0] < 0) {
                            y[0] = tp.getY();
                        }
                    }
                }
            }.getText(doc);
            return y[0];
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    @Timeout(120)
    void renderChineseToPdf() {
        String html = "<h1>劳动合同书</h1>"
                + "<p>甲方（用人单位）：<span data-field=\"party_a\" class=\"ct-var\">甲方名称</span></p>"
                + "<p>乙方（劳动者）：张三</p>"
                + "<table><thead><tr><th>条款</th><th>内容</th></tr></thead>"
                + "<tbody><tr><td>合同期限</td><td>2026-01-01 至 2028-12-31</td></tr>"
                + "<tr><td>工作地点</td><td>北京市海淀区</td></tr></tbody></table>"
                + "<p>本合同一式两份，甲乙双方各执一份，具有同等法律效力。</p>";

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("party_a", "北京某某科技有限公司");

        String filled = ContractHtmlProcessor.fillChips(html, values);
        byte[] pdf = new OpenHtmlToPdfClient().render(filled, PaperSize.A4, null, null, null, null);

        assertNotNull(pdf);
        assertTrue(pdf.length > 10_000, "PDF 应含内嵌字体，体积应显著大于空文档: " + pdf.length);
        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII),
                java.util.Arrays.copyOfRange(pdf, 0, 4), "应为 PDF 魔数开头");
    }

    @Test
    @Timeout(120)
    void renderTableCompactRowsTwoColumns() throws Exception {
        String raw = "<table><tbody>"
                + "<tr><th><p>A</p></th><th><p>B</p></th></tr>"
                + "<tr><td><p>1</p></td><td><p>2</p></td></tr>"
                + "<tr><td><p>6</p></td><td><p>5</p></td></tr>"
                + "</tbody></table>";
        String prepared = ContractHtmlProcessor.fillChips(ContractHtmlProcessor.sanitize(raw), null);
        byte[] pdf = new OpenHtmlToPdfClient().render(prepared, PaperSize.A4, null, null, null, null);
        java.util.Map<Character, Float> glyphY = new java.util.LinkedHashMap<>();
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            java.util.List<org.apache.pdfbox.text.TextPosition> pos = new java.util.ArrayList<>();
            new org.apache.pdfbox.text.PDFTextStripper() {
                { setSortByPosition(true); }
                @Override protected void writeString(String t, java.util.List<org.apache.pdfbox.text.TextPosition> p) {
                    for (var tp : p) {
                        char u = tp.getUnicode().charAt(0);
                        if ("AB1265".indexOf(u) >= 0 && !glyphY.containsKey(u)) {
                            glyphY.put(u, tp.getY());
                        }
                    }
                }
            }.getText(doc);
        }
        // 两列同基线（表格未塌成单列）
        assertTrue(Math.abs(glyphY.get('A') - glyphY.get('B')) < 2f, "A/B 应同行: " + glyphY);
        assertTrue(Math.abs(glyphY.get('1') - glyphY.get('2')) < 2f, "1/2 应同行");
        // 行距紧凑：预期 ≈ 行高20pt+内边距7.5pt < 48pt（回归前 116pt）
        float pitch = glyphY.get('1') - glyphY.get('A');
        assertTrue(pitch > 0 && pitch < 48f, "行距应紧凑, 实测 " + pitch + "pt");
        assertTrue(glyphY.get('6') - glyphY.get('1') < 48f, "第二行距同口径");
    }

    @Test
    @Timeout(120)
    void renderMarginPresetShiftsFirstLine() throws Exception {
        String html = "<p>首行</p>";
        var client = new OpenHtmlToPdfClient();
        byte[] narrow = client.render(html, PaperSize.A4, null, null, null,
                com.forge.modules.contract.enums.PaperMarginPreset.NARROW);
        byte[] wide = client.render(html, PaperSize.A4, null, null, null,
                com.forge.modules.contract.enums.PaperMarginPreset.WIDE);
        float yNarrow = firstGlyphY(narrow, "首");
        float yWide = firstGlyphY(wide, "首");
        // 窄档上边距 12.7mm vs 宽档 25.4mm：宽档首行应下移 ≈12.7mm≈36pt
        assertTrue(yWide - yNarrow > 25f, "宽档首行应显著下移: narrow=" + yNarrow + " wide=" + yWide);
    }

    @Test
    @Timeout(120)
    void renderTableCellCenterAlignTakesEffect() throws Exception {
        // 单元格对齐写在 td>p（tiptap 结构，sanitize 不解包）；断言居中格文本 x 明显大于左对齐格
        String raw = "<table><tbody><tr>"
                + "<td><p style=\"text-align: left\">左</p></td>"
                + "<td><p style=\"text-align: center\">中</p></td>"
                + "</tr></tbody></table>";
        String prepared = ContractHtmlProcessor.fillChips(ContractHtmlProcessor.sanitize(raw), null);
        byte[] pdf = new OpenHtmlToPdfClient().render(prepared, PaperSize.A4, null, null, null, null);
        java.util.Map<Character, Float> gx = new java.util.LinkedHashMap<>();
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            new org.apache.pdfbox.text.PDFTextStripper() {
                { setSortByPosition(true); }
                @Override protected void writeString(String t, java.util.List<org.apache.pdfbox.text.TextPosition> ps) {
                    for (var tp : ps) {
                        char u = tp.getUnicode().charAt(0);
                        if ((u == '左' || u == '中') && !gx.containsKey(u)) {
                            gx.put(u, tp.getX());
                        }
                    }
                }
            }.getText(doc);
        }
        assertTrue(gx.get('中') - gx.get('左') > 40f,
                "居中格文本应明显右移: 左=" + gx.get('左') + " 中=" + gx.get('中'));
    }

    @Test
    @Timeout(120)
    void renderListsPipelineBulletAndNumber() throws Exception {
        // 端到端：sanitize(li>p 解包) → fillChips(注入圆点) → PDF 文本层圆点与序号都在
        String raw = "<ul><li><p>甲项</p></li></ul><ol><li><p>第一项</p></li></ol>";
        String prepared = ContractHtmlProcessor.fillChips(ContractHtmlProcessor.sanitize(raw), null);
        byte[] pdf = new OpenHtmlToPdfClient().render(prepared, PaperSize.A4, null, null, null, null);
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", "");
            assertTrue(text.contains("\u2022"), "无序列表圆点应出现在 PDF: " + text);
            assertTrue(text.contains("1."), "有序列表序号应在");
        }
    }

    @Test
    @Timeout(120)
    void renderHighlightAndFontSize() {
        String html = "<p><mark style=\"background-color: #ffff00\">重点条款</mark> "
                + "<span style=\"font-size: 18pt\">大号条款文本</span></p>";
        byte[] pdf = new OpenHtmlToPdfClient().render(html, PaperSize.A4, null, null, null, null);
        assertTrue(pdf.length > 3_000, "含高亮与字号的 PDF 应正常产出: " + pdf.length);
    }

    @Test
    @Timeout(120)
    void renderLandscapeSwapsPageDimensions() throws Exception {
        String html = "<h2>横向合同</h2><p>宽幅条款内容。</p>";
        byte[] pdf = new OpenHtmlToPdfClient().render(html, PaperSize.A4, null, null,
                com.forge.modules.contract.enums.PaperOrientation.LANDSCAPE, null);
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var box = doc.getPage(0).getMediaBox();
            // A4 横向：297mm≈841.9pt 宽 > 595.3pt 高
            assertTrue(box.getWidth() > 800 && box.getHeight() < 600,
                    "横向应交换宽高, 实测 " + box.getWidth() + "x" + box.getHeight());
        }
    }

    @Test
    @Timeout(120)
    void renderPreservesConsecutiveSpaces() throws Exception {
        // 真机回归：模板维护的连续空格在 PDF 中不得被 CSS normal 折叠
        String html = "<p>甲方：A公司      乙方：B公司</p>";
        byte[] pdf = new OpenHtmlToPdfClient().render(html, PaperSize.A4, null, null, null, null);
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            String text = stripper.getText(doc);
            assertTrue(text.contains("A公司      乙方") || text.contains("A公司" + "　"),
                    "连续空格应保留在 PDF 文本层: [" + text.replace("\n", "\\n") + "]");
        }
    }

    @Test
    @Timeout(120)
    void renderCustomPaperAndWatermark() throws Exception {
        String html = "<h2>保密协议</h2><p>双方本着诚实信用原则，约定如下保密义务。</p>";
        OpenHtmlToPdfClient client = new OpenHtmlToPdfClient();
        byte[] pdf = client.render(html, PaperSize.CUSTOM, new BigDecimal("148"), new BigDecimal("210"), null, null);
        assertTrue(pdf.length > 5_000);

        byte[] stamped = new WatermarkStamper().stamp(pdf, "北京某某科技有限公司 · 定稿");
        assertTrue(stamped.length > pdf.length, "盖章后体积应增长");
        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII),
                java.util.Arrays.copyOfRange(stamped, 0, 4));
        // 平铺：单页 3×3 网格，水印文本应出现 9 次
        try (var doc = org.apache.pdfbox.Loader.loadPDF(stamped)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            // 斜排文本提取会插入空白/换行，归一化后按公司名关键词计数
            String text = stripper.getText(doc).replaceAll("\\s+", "");
            int count = text.split("北京某某科技有限公司", -1).length - 1;
            assertTrue(count == 9, "平铺水印应出现 9 次, 实测 " + count);
        }
    }
}
