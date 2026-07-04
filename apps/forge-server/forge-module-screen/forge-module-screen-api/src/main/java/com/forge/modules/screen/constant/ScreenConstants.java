package com.forge.modules.screen.constant;

/**
 * 大屏模块常量
 *
 * @author standadmin
 */
public final class ScreenConstants {

    private ScreenConstants() {
    }

    /**
     * 大屏权限前缀
     */
    public static final String PERM_PREFIX = "screen:screen";

    /**
     * 数据源权限前缀
     */
    public static final String PERM_DATA_SOURCE_PREFIX = "screen:data-source";

    /**
     * 查看权限后缀
     */
    public static final String PERM_VIEW_SUFFIX = "view";

    /**
     * 编辑权限后缀
     */
    public static final String PERM_EDIT = "edit";

    /**
     * SQL 最大返回行数
     */
    public static final int SQL_MAX_ROWS = 1000;

    /**
     * SQL 执行超时（毫秒）
     */
    public static final int SQL_TIMEOUT_MS = 5000;

    /**
     * HTTP 请求超时（毫秒）
     */
    public static final int HTTP_TIMEOUT_MS = 5000;

    /**
     * HTTP 响应体最大字节数
     */
    public static final int HTTP_MAX_BODY_BYTES = 1024 * 1024;

    /**
     * 数据源缓存 key 前缀
     */
    public static final String CACHE_PREFIX = "screen:ds:";

    /**
     * 熔断器缓存 key 前缀
     */
    public static final String CIRCUIT_BREAKER_PREFIX = "screen:cb:";
}
