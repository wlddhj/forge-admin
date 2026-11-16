package com.forge.modules.contract.framework.pdf;

import com.forge.modules.contract.enums.PaperSize;

import java.math.BigDecimal;

/**
 * HTML→PDF 渲染 SPI（对齐 FileStorageClient 模式）。
 * 当前唯一实现 {@link OpenHtmlToPdfClient}，为未来引擎（含付费方案）保留切换缝。
 *
 * @author forge-admin
 */
public interface PdfRenderClient {

    /**
     * 引擎标识
     */
    String engineType();

    /**
     * 渲染合同内容为 PDF
     *
     * @param contentHtml 已填充变量的合同内容 HTML（body 内层）
     * @param paper       纸张规格（CUSTOM 时以 customWidthMm/customHeightMm 为准）
     * @param customWidthMm  自定义纸宽 mm（可空）
     * @param customHeightMm 自定义纸高 mm（可空）
     * @return PDF 字节
     */
    byte[] render(String contentHtml, PaperSize paper, BigDecimal customWidthMm, BigDecimal customHeightMm);
}
