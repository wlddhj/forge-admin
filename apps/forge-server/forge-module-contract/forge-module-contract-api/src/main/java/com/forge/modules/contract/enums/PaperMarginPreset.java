package com.forge.modules.contract.enums;

import java.math.BigDecimal;

/**
 * 页边距档位（Word 同口径：窄/正常/宽）——渲染时注入 @page margin
 *
 * @author forge-admin
 */
public enum PaperMarginPreset {

    /** 窄：上下左右各 12.7mm */
    NARROW("NARROW", "12.7", "12.7"),
    /** 正常：上下 25.4mm，左右 31.8mm */
    NORMAL("NORMAL", "25.4", "31.8"),
    /** 宽：上下 25.4mm，左右 50.8mm */
    WIDE("WIDE", "25.4", "50.8");

    private final String code;
    private final BigDecimal topBottomMm;
    private final BigDecimal leftRightMm;

    PaperMarginPreset(String code, String topBottomMm, String leftRightMm) {
        this.code = code;
        this.topBottomMm = new BigDecimal(topBottomMm);
        this.leftRightMm = new BigDecimal(leftRightMm);
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getTopBottomMm() {
        return topBottomMm;
    }

    public BigDecimal getLeftRightMm() {
        return leftRightMm;
    }

    /** 按 code 解析，非法/空值回退正常档 */
    public static PaperMarginPreset of(String code) {
        for (PaperMarginPreset m : values()) {
            if (m.code.equalsIgnoreCase(code)) {
                return m;
            }
        }
        return NORMAL;
    }
}
