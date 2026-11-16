package com.forge.modules.contract.framework.pdf;

import com.forge.modules.contract.enums.PaperOrientation;
import com.forge.modules.contract.enums.PaperSize;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * openhtmltopdf 渲染实现（社区后继 fork io.github.openhtmltopdf，PDFBox 3）。
 * 进程内渲染无常驻进程；中文经内置思源 TTF 注册（resources/fonts）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class OpenHtmlToPdfClient implements PdfRenderClient {

    private static final String SERIF_FONT = "/fonts/NotoSerifSC.ttf";
    private static final String SANS_FONT = "/fonts/NotoSansSC.ttf";
    private static final String SERIF_FAMILY = "Noto Serif SC";
    private static final String SANS_FAMILY = "Noto Sans SC";

    /** 字体字节进程内仅加载一次（合计约 41MB） */
    private static final byte[] SERIF_TTF = loadFont(SERIF_FONT);
    private static final byte[] SANS_TTF = loadFont(SANS_FONT);

    @Override
    public String engineType() {
        return "openhtmltopdf";
    }

    @Override
    public byte[] render(String contentHtml, PaperSize paper, BigDecimal customWidthMm, BigDecimal customHeightMm,
                         PaperOrientation orientation) {
        // 空段落（回车空行）补 <br/> 占行高：CSS 生成内容(::before)在 openhtmltopdf 无效（探针实测
        // 3 空段渲染 0 空行），含 br 的段落块高度=一行是引擎确定行为；所有渲染路径（预览/打印/定稿）统一走此
        String normalized = contentHtml.replaceAll("<p>(\\s|&nbsp;|\\u00A0)*</p>", "<p><br/></p>");
        String html = "<html><head><style>" + buildPageCss(paper, customWidthMm, customHeightMm, orientation) + BASE_CSS
                + "</style></head><body>" + normalized + "</body></html>";
        // openhtmltopdf 要求良构 XHTML：jsoup 解析 → XML 语法 → W3C DOM（jsoup 官方桥接）
        Document jdoc = Jsoup.parse(html);
        jdoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        org.w3c.dom.Document w3c = new W3CDom().fromJsoup(jdoc);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(() -> new ByteArrayInputStream(SERIF_TTF), SERIF_FAMILY);
            builder.useFont(() -> new ByteArrayInputStream(SANS_TTF), SANS_FAMILY);
            builder.withW3cDocument(w3c, "/");
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("合同 PDF 渲染失败: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /**
     * 按纸张注入 @page（尺寸 + 页边距 + 页脚页码），自定义毫米值优先
     */
    private String buildPageCss(PaperSize paper, BigDecimal customWidthMm, BigDecimal customHeightMm,
                                PaperOrientation orientation) {
        BigDecimal w = paper.getWidthMm();
        BigDecimal h = paper.getHeightMm();
        if (paper == PaperSize.CUSTOM && customWidthMm != null && customHeightMm != null
                && customWidthMm.signum() > 0 && customHeightMm.signum() > 0) {
            w = customWidthMm;
            h = customHeightMm;
        }
        if (w == null || h == null) {
            w = PaperSize.A4.getWidthMm();
            h = PaperSize.A4.getHeightMm();
        }
        // 横向：交换宽高（自定义纸张同理，宽高按纵向口径填写）
        if (orientation == PaperOrientation.LANDSCAPE) {
            BigDecimal tmp = w;
            w = h;
            h = tmp;
        }
        return "@page { size: " + w.stripTrailingZeros().toPlainString() + "mm " + h.stripTrailingZeros().toPlainString()
                + "mm; margin: 25.4mm 31.8mm;"
                + " @bottom-center { content: counter(page) \" / \" counter(pages);"
                + " font-family: '" + SANS_FAMILY + "'; font-size: 9pt; color: #666; } }";
    }

    /** 合同专用基础样式：全部落在渲染器 CSS 2.1 + Paged Media 支持面内 */
    private static final String BASE_CSS = ""
            + "body { font-family: '" + SERIF_FAMILY + "'; font-size: 12pt; line-height: 1.7; color: #000; }"
            // 模板维护的连续空格/行首缩进须与编辑器所见一致（tiptap 编辑区默认 pre-wrap），
            // normal 会折叠连续空格——真机反馈「空格被忽略」的根因
            + "p, h1, h2, h3, h4, li, td, th, blockquote { white-space: pre-wrap; }"
            + "h1 { font-family: '" + SANS_FAMILY + "'; font-size: 18pt; text-align: center; margin: 0 0 14pt; break-after: avoid; }"
            + "h2 { font-family: '" + SANS_FAMILY + "'; font-size: 15pt; margin: 12pt 0 6pt; break-after: avoid; }"
            + "h3, h4 { font-family: '" + SANS_FAMILY + "'; font-size: 13pt; margin: 10pt 0 5pt; break-after: avoid; }"
            + "p { margin: 0 0 6pt; text-align: justify; }"
            + "table { width: 100%; border-collapse: collapse; margin: 8px 0; }"
            + "th, td { border: 1px solid #333; padding: 5px 8px; }"
            + "th { font-family: '" + SANS_FAMILY + "'; background-color: #f2f2f2; }"
            + "tr { break-inside: avoid; }"
            + "ul, ol { margin: 0 0 8px; padding-left: 32px; }"
            + "ul { list-style-type: none; }"
            + "ol { list-style-type: decimal; }"
            + ".ct-li-mark { padding-right: 4px; }"
            + "li { margin: 0 0 3px; break-inside: avoid; }"
            + "li > p { margin: 0; }"
            + "th > p, td > p { margin: 0; }"
            + "blockquote { margin: 0 0 8px; padding-left: 32px; border-left: 3px solid #ccc; color: #555; }"
            + "img { max-width: 100%; }"
            + "a { color: #000; text-decoration: none; }";

    private static byte[] loadFont(String path) {
        try (InputStream in = OpenHtmlToPdfClient.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("字体文件缺失: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("字体文件加载失败: " + path, e);
        }
    }
}
