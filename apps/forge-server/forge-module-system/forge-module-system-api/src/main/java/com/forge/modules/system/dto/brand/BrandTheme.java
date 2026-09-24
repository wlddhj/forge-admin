package com.forge.modules.system.dto.brand;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 系统默认主题（新用户/无本地配置用户首次进入时的初始主题）
 *
 * palette 不含 custom：系统默认主题无自定义主色配置入口
 */
@Data
public class BrandTheme {

    public static final String DEFAULT_PALETTE = "blue";
    public static final String DEFAULT_LAYOUT = "sidebar";
    public static final String DEFAULT_STYLE = "flat";
    public static final String DEFAULT_MODE = "auto";

    @Pattern(regexp = "blue|purple|green|crimson|orange|cyan|teal", message = "调色板取值不合法")
    private String palette;

    @Pattern(regexp = "sidebar|top", message = "布局取值不合法")
    private String layout;

    @Pattern(regexp = "flat|glass|card|compact", message = "风格取值不合法")
    private String style;

    @Pattern(regexp = "light|dark|auto", message = "明暗模式取值不合法")
    private String mode;

    /**
     * 逐字段回退默认值（null/空白/白名单外 → 默认；白名单外防御 sys_config 被直接改库），
     * null 入参返回全新默认对象
     */
    public static BrandTheme normalize(BrandTheme theme) {
        BrandTheme result = new BrandTheme();
        if (theme == null) {
            result.setPalette(DEFAULT_PALETTE);
            result.setLayout(DEFAULT_LAYOUT);
            result.setStyle(DEFAULT_STYLE);
            result.setMode(DEFAULT_MODE);
            return result;
        }
        result.setPalette(sanitize(theme.getPalette(), "blue|purple|green|crimson|orange|cyan|teal", DEFAULT_PALETTE));
        result.setLayout(sanitize(theme.getLayout(), "sidebar|top", DEFAULT_LAYOUT));
        result.setStyle(sanitize(theme.getStyle(), "flat|glass|card|compact", DEFAULT_STYLE));
        result.setMode(sanitize(theme.getMode(), "light|dark|auto", DEFAULT_MODE));
        return result;
    }

    private static String sanitize(String value, String whitelist, String fallback) {
        return value != null && value.matches(whitelist) ? value : fallback;
    }
}
