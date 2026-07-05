package com.forge.modules.screen.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.screen.config.ScreenProperties;
import com.forge.modules.screen.safety.SqlSafetyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

/**
 * 大屏 HTTP 数据源执行器：在 {@link ScreenProperties#getAllowedHosts()}
 * 白名单约束下安全地发起 HTTP 调用，返回解析后的 JSON（{@code Object}）。
 *
 * <h3>SSRF 防护三道闸门</h3>
 * <ol>
 *   <li><b>URI 合法性</b>：URL 必须可解析为 {@link URI}，且 {@code host != null}。</li>
 *   <li><b>host 精确白名单匹配</b>（大小写不敏感）：使用
 *       {@code allowedHosts.stream().anyMatch(h -> h.equalsIgnoreCase(host))}，
 *       <b>绝不</b>使用 {@code contains(host)} 子串匹配。
 *       <p>关键安全语义：子串匹配可被 {@code evil.com.127.0.0.1} 形式绕过，
 *       故必须严格等值比较。允许 {@code LOCALHOST == localhost} 仅作大小写兼容。
 *       <p>DNS rebinding 不在本任务范围（需 host→IP 解析后再比对内网段，
 *       后续可叠加 {@code InetAddress.getByName} + 私网段校验）。</li>
 *   <li><b>HTTPS 强制</b>：当 {@link ScreenProperties#isRequireHttps()} 为真时，
 *       scheme 必须为 {@code https}（大小写不敏感）。</li>
 * </ol>
 *
 * <h3>请求构造</h3>
 * <ul>
 *   <li>使用 {@link RestClient}（Spring 6.1+）。</li>
 *   <li>连接 + 读取超时取自 {@link ScreenProperties#getHttpTimeoutMs()}。</li>
 *   <li>方法名来自 {@code config.get("method")}，缺省 {@code GET}。</li>
 *   <li>响应体以 {@code String} 形式获取，再用 Jackson 解析为 {@code Object}；
 *       解析失败时回退为原始字符串（保证调用方能拿到数据）。</li>
 * </ul>
 *
 * <h3>测试性</h3>
 * <p>{@code restClient} 可注入。测试场景下传 {@code null}，
 * 实现仅在真正发起请求时才需要 RestClient（延迟构造），
 * 使 SSRF 闸门可在无 mock 环境下独立验证。
 *
 * <p>所有外部失败统一抛 {@link SqlSafetyException}，
 * 便于上层 Controller 用同一异常处理器映射 4xx。
 *
 * @author standadmin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpDataSourceExecutor {

    private final ScreenProperties props;
    /** 可注入；为 null 时在执行阶段延迟构造。 */
    private final RestClient restClient;

    /**
     * 执行 HTTP 调用并解析 JSON。
     *
     * @param config 数据源配置，必含 {@code url}，可选 {@code method}（默认 GET）
     * @param params 预留：未来支持 query/header 模板替换
     * @return 解析后的 JSON（Map / List / 标量），或原始字符串
     * @throws SqlSafetyException SSRF 闸门失败（host 不在白名单、HTTPS 强制失败、URL 非法）
     */
    public Object execute(Map<String, Object> config, Map<String, Object> params) {
        // ① 提取 url
        Object urlObj = config.get("url");
        if (!(urlObj instanceof String url) || url.isBlank()) {
            throw new SqlSafetyException("HTTP 数据源缺少 url");
        }

        // ② SSRF 闸门 1：URI 合法性
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new SqlSafetyException("非法 url: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            // 例：形如 "http:///api/x" 或 "file:///etc/passwd"
            throw new SqlSafetyException("url 缺少 host: " + url);
        }

        // ③ SSRF 闸门 2：host 精确白名单匹配（大小写不敏感）
        //    严格使用 equalsIgnoreCase —— 子串 contains 会被 "evil.com.127.0.0.1" 绕过。
        boolean allowed = props.getAllowedHosts().stream()
            .anyMatch(h -> h != null && h.equalsIgnoreCase(host));
        if (!allowed) {
            throw new SqlSafetyException("host 不在白名单: " + host);
        }

        // ④ SSRF 闸门 3：HTTPS 强制
        String scheme = uri.getScheme();
        if (props.isRequireHttps() && !"https".equalsIgnoreCase(scheme)) {
            throw new SqlSafetyException("生产环境强制 HTTPS: " + url);
        }

        // ⑤ 通过闸门后，构造 RestClient 并发起请求
        //    所有运行时异常（连接拒绝、HTTP 4xx/5xx、超时）统一包装为
        //    SqlSafetyException，符合"执行器对外只抛 SqlSafetyException"的契约。
        String method = config.getOrDefault("method", "GET").toString();
        RestClient client = buildClient();

        String body;
        try {
            body = client.method(HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)))
                .uri(uri)
                .retrieve()
                .body(String.class);
        } catch (RuntimeException e) {
            log.warn("HTTP 调用失败; url={}, err={}", url, e.getMessage());
            throw new SqlSafetyException("HTTP 调用失败: " + e.getMessage());
        }

        // ⑥ 解析 JSON；失败回退为原始字符串
        if (body == null) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(body, Object.class);
        } catch (Exception e) {
            log.debug("HTTP 响应非 JSON，返回原始字符串; url={}, len={}", url, body.length());
            return body;
        }
    }

    /**
     * 构造 RestClient：若已注入则直接复用，否则按 {@link ScreenProperties#getHttpTimeoutMs()}
     * 构造一个 {@link SimpleClientHttpRequestFactory} 实例。
     *
     * <p>{@code SimpleClientHttpRequestFactory.setConnectTimeout/setReadTimeout} 接受 int 毫秒。
     */
    private RestClient buildClient() {
        if (restClient != null) {
            return restClient;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getHttpTimeoutMs());
        factory.setReadTimeout(props.getHttpTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }
}
