package com.forge.modules.screen.enums;

import lombok.Getter;

/**
 * 大屏状态枚举
 *
 * @author standadmin
 */
@Getter
public enum ScreenStatus {
    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布");

    private final int code;
    private final String label;

    ScreenStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据 code 解析枚举
     */
    public static ScreenStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ScreenStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
