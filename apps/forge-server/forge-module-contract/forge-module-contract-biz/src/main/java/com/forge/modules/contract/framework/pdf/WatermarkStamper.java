package com.forge.modules.contract.framework.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 定稿水印盖章（PDFBox 逐页 45° 淡色大字，引擎无关——不依赖 CSS @page 背景兼容性）。
 * 水印文案形如「XX公司 · 定稿」「XX公司 · 草稿」。
 *
 * @author forge-admin
 */
@Component
public class WatermarkStamper {

    private static final String WATERMARK_FONT = "/fonts/NotoSansSC.ttf";
    /** 字体字节进程内仅加载一次 */
    private static final byte[] FONT_TTF = loadFontBytes();
    private static final float FONT_SIZE = 52f;
    private static final float ALPHA = 0.12f;
    private static final double ROTATE_RAD = Math.toRadians(45);

    /**
     * 为 PDF 每页盖水印，返回新 PDF 字节
     */
    public byte[] stamp(byte[] pdf, String text) {
        try (PDDocument doc = Loader.loadPDF(pdf);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(doc, new ByteArrayInputStream(FONT_TTF), true);
            for (PDPage page : doc.getPages()) {
                stampPage(doc, page, font, text);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("水印盖章失败: " + e.getMessage(), e);
        }
    }

    private void stampPage(PDDocument doc, PDPage page, PDType0Font font, String text) throws IOException {
        PDRectangle box = page.getMediaBox();
        float textWidth = font.getStringWidth(text) / 1000f * FONT_SIZE;
        float x = (box.getWidth() - textWidth) / 2f;
        float y = box.getHeight() / 4f;

        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(ALPHA);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            cs.setGraphicsStateParameters(gs);
            cs.setNonStrokingColor(new Color(130, 130, 130));
            cs.beginText();
            cs.setFont(font, FONT_SIZE);
            cs.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(ROTATE_RAD, x, y));
            cs.showText(text);
            cs.endText();
        }
    }

    private static byte[] loadFontBytes() {
        try (InputStream in = WatermarkStamper.class.getResourceAsStream(WATERMARK_FONT)) {
            if (in == null) {
                throw new IllegalStateException("水印字体文件缺失: " + WATERMARK_FONT);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("水印字体文件加载失败: " + WATERMARK_FONT, e);
        }
    }
}
