package com.forge.modules.contract.enums;

import java.math.BigDecimal;

/**
 * 合同纸张规格（毫米），渲染时注入 @page size
 *
 * @author forge-admin
 */
public enum PaperSize {

    A4("A4", "210", "297"),
    A5("A5", "148", "210"),
    B3("B3", "250", "353"),
    LETTER("LETTER", "215.9", "279.4"),
    LEGAL("LEGAL", "215.9", "355.6"),
    /** 自定义（毫米值由模板/打印参数携带） */
    CUSTOM("CUSTOM", null, null);

    private final String code;
    private final BigDecimal widthMm;
    private final BigDecimal heightMm;

    PaperSize(String code, String widthMm, String heightMm) {
        this.code = code;
        this.widthMm = widthMm == null ? null : new BigDecimal(widthMm);
        this.heightMm = heightMm == null ? null : new BigDecimal(heightMm);
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getWidthMm() {
        return widthMm;
    }

    public BigDecimal getHeightMm() {
        return heightMm;
    }

    /**
     * 按 code 解析，非法值回退 A4
     */
    public static PaperSize of(String code) {
        for (PaperSize ps : values()) {
            if (ps.code.equalsIgnoreCase(code)) {
                return ps;
            }
        }
        return A4;
    }
}
