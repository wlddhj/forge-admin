package com.forge.modules.contract.enums;

/**
 * 纸张方向：横向时渲染引擎交换 @page 宽高
 *
 * @author forge-admin
 */
public enum PaperOrientation {

    PORTRAIT("PORTRAIT", "纵向"),
    LANDSCAPE("LANDSCAPE", "横向");

    private final String code;
    private final String label;

    PaperOrientation(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 解析，非法/空值回退纵向 */
    public static PaperOrientation of(String code) {
        for (PaperOrientation o : values()) {
            if (o.code.equalsIgnoreCase(code)) {
                return o;
            }
        }
        return PORTRAIT;
    }
}
