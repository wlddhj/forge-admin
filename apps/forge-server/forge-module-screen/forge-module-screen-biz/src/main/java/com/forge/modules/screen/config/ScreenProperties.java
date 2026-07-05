package com.forge.modules.screen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 大屏模块安全配置项。
 *
 * <p>对应 {@code application.yml} 中的 {@code forge.security.screen.*}，
 * 控制 {@link com.forge.modules.screen.executor.HttpDataSourceExecutor} 的 SSRF
 * 防护放行域名、HTTP 超时、最大响应体大小以及是否强制 HTTPS。
 *
 * <h3>关键字段</h3>
 * <ul>
 *   <li><b>allowedHosts</b>：HTTP 数据源 host 白名单（精确、大小写不敏感匹配）。
 *       默认放行内网常用域名/IP；生产环境应通过配置文件显式覆盖。</li>
 *   <li><b>httpTimeoutMs</b>：连接与读取超时毫秒数（默认 5000ms）。</li>
 *   <li><b>httpMaxBodyBytes</b>：响应体最大字节（默认 1MB，超出由上层裁剪/拒绝）。</li>
 *   <li><b>requireHttps</b>：生产环境强烈建议设为 true，强制所有外部数据源走 HTTPS。</li>
 * </ul>
 *
 * @author standadmin
 */
@Data
@ConfigurationProperties(prefix = "forge.security.screen")
public class ScreenProperties {

    /** HTTP 数据源 host 白名单（精确匹配，大小写不敏感比较由执行器实现） */
    private List<String> allowedHosts = new ArrayList<>();

    /** HTTP 连接 + 读取超时（毫秒） */
    private int httpTimeoutMs = 5000;

    /** HTTP 响应体最大字节（默认 1MB） */
    private long httpMaxBodyBytes = 1024 * 1024;

    /** 是否强制 HTTPS（生产环境建议 true） */
    private boolean requireHttps = false;

    /**
     * 返回带常用内网域名/IP 的默认配置：{@code localhost}、{@code 127.0.0.1}、
     * Compose 服务名 {@code forge-server} / {@code forge-ai-python}。
     *
     * <p>用于测试夹具，以及在未配置时提供安全合理的兜底（仅允许内网，
     * 不放行任意公网域名，避免 SSRF 默认开放）。
     */
    public static ScreenProperties defaults() {
        ScreenProperties p = new ScreenProperties();
        p.allowedHosts = new ArrayList<>(List.of(
            "localhost", "127.0.0.1", "forge-server", "forge-ai-python"
        ));
        return p;
    }
}
