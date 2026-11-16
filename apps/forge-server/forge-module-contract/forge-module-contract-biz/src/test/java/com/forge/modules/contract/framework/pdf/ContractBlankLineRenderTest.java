package com.forge.modules.contract.framework.pdf;
import com.forge.modules.contract.enums.PaperSize;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
class ContractBlankLineRenderTest {
    /**
     * 真机回归：模板回车空行(<p></p>)在 PDF 中须保留整行高度。
     * 断言口径用 AA/BB 两行 Y 坐标差（文本提取器对无字形空行不输出，不能按空行文本计数）
     */
    @Test
    void blankParagraphsKeepLineHeight() throws Exception {
        String html = "<p>AA行</p><p></p><p></p><p></p><p>BB行</p>";
        byte[] pdf = new OpenHtmlToPdfClient().render(html, PaperSize.A4, null, null);
        Files.write(Paths.get("/tmp/blank_probe.pdf"), pdf);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<TextPosition> positions = new ArrayList<>();
            new PDFTextStripper() {
                { setSortByPosition(true); }
                @Override
                protected void writeString(String text, List<TextPosition> pos) {
                    positions.addAll(pos);
                }
            }.getText(doc);
            float yA = 0, yB = 0, lineH = 0;
            for (TextPosition p : positions) {
                String u = p.getUnicode();
                if (u.contains("A") && !u.contains("B")) { yA = p.getY(); lineH = p.getHeight(); }
                if (u.contains("B") && !u.contains("A")) { yB = p.getY(); }
            }
            // 行高≈12pt*1.7≈20.4pt；AA+3空行+BB=5 行 → 间距应 > 4.5 行（无空行时仅 ≈1 行）
            double gapLines = (yB - yA) / (12 * 1.7);
            org.junit.jupiter.api.Assertions.assertTrue(gapLines > 4.5,
                    "3 个空段落应撑起 >=4.5 行间距, 实测 gapLines=" + gapLines);
        }
    }
}
