package com.forge.modules.system.dto.brand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * BrandTheme 纯静态单测（无 Spring 上下文）
 */
class BrandThemeTest {

    @Test
    void null入参返回全新默认对象() {
        BrandTheme result = BrandTheme.normalize(null);
        assertNotNull(result);
        assertEquals(BrandTheme.DEFAULT_PALETTE, result.getPalette());
        assertEquals(BrandTheme.DEFAULT_LAYOUT, result.getLayout());
        assertEquals(BrandTheme.DEFAULT_STYLE, result.getStyle());
        assertEquals(BrandTheme.DEFAULT_MODE, result.getMode());
        assertEquals(BrandTheme.DEFAULT_SIDEBAR, result.getSidebar());
    }

    @Test
    void 合法值原样保留() {
        BrandTheme theme = new BrandTheme();
        theme.setPalette("teal");
        theme.setLayout("top");
        theme.setStyle("glass");
        theme.setMode("dark");
        theme.setSidebar("light");
        BrandTheme result = BrandTheme.normalize(theme);
        assertEquals("teal", result.getPalette());
        assertEquals("top", result.getLayout());
        assertEquals("glass", result.getStyle());
        assertEquals("dark", result.getMode());
        assertEquals("light", result.getSidebar());
    }

    @Test
    void 白名单外与空白值逐字段回退默认() {
        BrandTheme theme = new BrandTheme();
        theme.setPalette("custom");   // 白名单外（palette 不含 custom）
        theme.setLayout("");          // 空白
        theme.setStyle("wrong");
        theme.setMode("brightness");  // 白名单外
        theme.setSidebar("pink");     // 白名单外 → 回退 DEFAULT_SIDEBAR
        BrandTheme result = BrandTheme.normalize(theme);
        assertEquals(BrandTheme.DEFAULT_PALETTE, result.getPalette());
        assertEquals(BrandTheme.DEFAULT_LAYOUT, result.getLayout());
        assertEquals(BrandTheme.DEFAULT_STYLE, result.getStyle());
        assertEquals(BrandTheme.DEFAULT_MODE, result.getMode());
        assertEquals(BrandTheme.DEFAULT_SIDEBAR, result.getSidebar());
    }

    @Test
    void null字段与其他合法字段互不影响() {
        BrandTheme theme = new BrandTheme();
        theme.setSidebar("light");    // 仅设 sidebar，其余 null
        BrandTheme result = BrandTheme.normalize(theme);
        assertEquals("light", result.getSidebar());
        assertEquals(BrandTheme.DEFAULT_PALETTE, result.getPalette());
        assertEquals(BrandTheme.DEFAULT_MODE, result.getMode());
    }
}
