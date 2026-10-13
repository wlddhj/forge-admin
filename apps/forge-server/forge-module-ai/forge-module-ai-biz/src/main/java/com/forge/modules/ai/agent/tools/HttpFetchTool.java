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
        String body = client.method(HttpMethod.valueOf(method))
                .uri(uri)
                .retrieve()
                .body(String.class);
        int limit = properties.getToolOutputLimit();
        return body != null && body.length() > limit ? body.substring(0, limit) + "…(截断)" : body;
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
