package com.forge.modules.ai.agent.tools;

import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * HTTP 抓取工具：仅允许访问配置白名单域名（SSRF 防护）
 */
@Component
@RequiredArgsConstructor
public class HttpFetchTool implements AgentTool {

    private final AgentProperties properties;

    private volatile RestClient restClient;

    @Override
    public String name() {
        return "http_fetch";
    }

    @Override
    public String description() {
        return "请求指定 URL 并返回响应文本（仅限白名单域名）";
    }

    @Override
    public Map<String, String> parameterSpec() {
        return Map.of("url", "目标 URL（http/https）", "method", "HTTP 方法，默认 GET");
    }

    @Override
    public Object execute(Map<String, Object> args) {
        List<String> allowedHosts = properties.getAllowedHosts();
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw new BusinessException("HTTP 工具未配置域名白名单，已禁用");
        }
        String url = String.valueOf(args.get("url"));
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new BusinessException("URL 非法: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException("仅允许 http/https 协议");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean allowed = allowedHosts.stream()
                .map(h -> h.toLowerCase().startsWith(".") ? h.toLowerCase() : "." + h.toLowerCase())
                .anyMatch(suffix -> ("." + host).endsWith(suffix));
        if (!allowed) {
            throw new BusinessException("目标域名不在白名单: " + host);
        }

        String method = String.valueOf(args.getOrDefault("method", "GET")).toUpperCase();
        RestClient client = client();
        int maxBytes = Math.max(1, properties.getHttpMaxResponseBytes());
        CappedRead capped = client.method(HttpMethod.valueOf(method))
                .uri(uri)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new BusinessException("HTTP 请求失败: " + response.getStatusCode());
                    }
                    try (java.io.InputStream body = response.getBody()) {
                        return readCapped(body, maxBytes);
                    }
                });
        // 双层截断:字节层防 OOM(流式读取),字符层防决策上下文膨胀
        String text = capped.body();
        int limit = properties.getToolOutputLimit();
        String result = text.length() > limit ? text.substring(0, limit) + "…(截断)" : text;
        if (capped.truncated()) {
            result += "\n…(响应超过 " + maxBytes + " 字节已截断)";
        }
        return result;
    }

    /** 字节上限读取结果 */
    record CappedRead(String body, boolean truncated) {
    }

    /**
     * 流式读取至多 maxBytes 字节(UTF-8),恰好等于上限时探测一个额外字节判断是否真截断
     */
    static CappedRead readCapped(java.io.InputStream in, int maxBytes) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int n;
        while (total < maxBytes && (n = in.read(chunk, 0, Math.min(chunk.length, maxBytes - total))) != -1) {
            buffer.write(chunk, 0, n);
            total += n;
        }
        boolean truncated = total >= maxBytes && in.read() != -1;
        return new CappedRead(buffer.toString(java.nio.charset.StandardCharsets.UTF_8), truncated);
    }

    private RestClient client() {
        if (restClient == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5_000);
            factory.setReadTimeout(15_000);
            restClient = RestClient.builder().requestFactory(factory).build();
        }
        return restClient;
    }
}
