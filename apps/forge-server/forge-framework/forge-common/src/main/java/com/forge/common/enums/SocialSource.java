package com.forge.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 社交登录来源枚举
 */
@Getter
@AllArgsConstructor
public enum SocialSource {

    WECHAT("wechat", "微信"),
    DINGTALK("dingtalk", "钉钉"),
    /** 企业微信（openId 列存企微 userid，用于 IM 工作通知绑定） */
    WEWORK("wework", "企业微信"),
    /** 钉钉企业 userid（openId 列存钉钉 userid，手动录入；无 OAuth，与 dingtalk 扫码登录并存） */
    DINGTALK_CORP("dingtalk_corp", "钉钉企业");

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
