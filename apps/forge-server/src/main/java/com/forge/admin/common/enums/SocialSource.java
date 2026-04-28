package com.forge.admin.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 社交登录来源枚举
 */
@Getter
@AllArgsConstructor
public enum SocialSource {

    WECHAT("wechat", "微信"),
    DINGTALK("dingtalk", "钉钉");

    private final String code;
    private final String displayName;

    public static SocialSource fromCode(String code) {
        for (SocialSource source : values()) {
            if (source.getCode().equalsIgnoreCase(code)) {
                return source;
            }
        }
        throw new IllegalArgumentException("不支持的社交平台: " + code);
    }
}
