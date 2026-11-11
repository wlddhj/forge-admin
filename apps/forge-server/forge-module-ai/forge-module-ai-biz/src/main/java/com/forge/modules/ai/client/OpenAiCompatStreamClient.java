package com.forge.modules.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.SequenceInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 原生 OpenAI 兼容 SSE 流客户端
 *
 * Spring AI 2.0（官方 OpenAI SDK）的 DTO 会静默丢弃 GLM 等思考模型的
 * delta.reasoning_content 扩展字段，本客户端直连上游按行解析原始 SSE，
 * 将 reasoning 与 content 增量分别透传为 {@link StreamFrame}。
 *
 * 上游非 200 抛 {@link UpstreamStreamException}，由调用层既有的
 * onErrorResume（聊天流转错误帧）/ failover（决策流竞速）语义兜底。
 *
 * @author standadmin
 */
@Slf4j
@Component
public class OpenAiCompatStreamClient {

    /**
     * 流最长挂钟时间（防上游挂起导致线程无限阻塞）：聊天/决策流远达不到该量级
     */
    private static final Duration MAX_STREAM_DURATION = Duration.ofMinutes(10);

    /** 建连超时（每节点） */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** 流中读超时：帧间隔可达数秒，放宽到 90s */
    private static final int READ_TIMEOUT_MS = 90_000;

    /** 最多尝试节点数（DNS 多 A 记录逐个探测） */
    private static final int MAX_NODE_ATTEMPTS = 3;

    /** 帧间隔超过该值视为中段停顿（正常 GLM 帧间隔 <1s，content 切换也 <2s） */
    private static final long STALL_MARK_MS = 5_000;

    /** 中段停顿节点的冷却时长（期间排序到最后，之后恢复参与） */
    private static final long NODE_COOLDOWN_MS = 5 * 60_000L;

    /** 节点冷却表：IP → 冷却截止时间戳（epoch ms） */
    private final ConcurrentHashMap<String, Long> badNodesUntil =
            new ConcurrentHashMap<>();

    private static final SSLSocketFactory SSL_SOCKET_FACTORY =
            (SSLSocketFactory) SSLSocketFactory.getDefault();

    /**
     * 进程内记忆最近成功节点：DNS 多 A 记录顺序随机（GA 边缘节点好坏不一），
     * 粘住好节点避免每次请求掷骰子
     */
    private final AtomicReference<String> lastGoodIp = new AtomicReference<>();

    public record StreamRequest(String url, String apiKey, String body, Duration headersTimeout) {

        public StreamRequest(String url, String apiKey, String body) {
            this(url, apiKey, body, Duration.ofSeconds(60));
        }
    }

    /**
     * 上游异常：状态码非 200 或流中断
     */
    public static class UpstreamStreamException extends RuntimeException {
        private final int statusCode;

        public UpstreamStreamException(int statusCode, String bodySnippet) {
            super("上游流式接口返回 " + statusCode + (bodySnippet == null || bodySnippet.isBlank()
                    ? "" : ": " + bodySnippet));
            this.statusCode = statusCode;
        }

        public UpstreamStreamException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = 0;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    private final ObjectMapper objectMapper;

    public OpenAiCompatStreamClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 流式调用：元素为解析后的帧，reasoning 与 content 增量分离透传。
     *
     * 注意：消费端应使用 unbounded 订阅（SseEmitter 桥/doOnNext 链）。
     * 不加 publishOn：真机实证高频帧流（~11ms/帧）下 publishOn 反而引入 20s+ 投递停顿；
     * MVC ReactiveTypeHandler 的 request(1) 消费模式与本链路的唤醒丢失问题由
     * Controller 层 SseEmitter 手工桥解决（见 AiChatController）
     */
    public Flux<StreamFrame> stream(StreamRequest request) {
        return Flux.<StreamFrame>create(emitter -> {
                    try {
                        doStream(request, emitter::next);
                        emitter.complete();
                    } catch (UpstreamStreamException e) {
                        emitter.error(e);
                    } catch (Exception e) {
                        emitter.error(new UpstreamStreamException("上游流式连接失败: " + e.getMessage(), e));
                    }
                })
                // 同步读循环放弹性线程，避免占死订阅线程
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 发起 HTTP + 按行解析（包私有读循环供单测复用：readFrames 接收任意 Reader 源）
     */
    void doStream(StreamRequest request, Consumer<StreamFrame> sink) throws IOException, InterruptedException {
        URI uri = URI.create(request.url());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new UpstreamStreamException("仅支持 https 流式端点: " + request.url(), null);
        }
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 443;
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());

        List<InetAddress> candidates = candidateAddresses(host);
        IOException last = null;
        AtomicInteger sent = new AtomicInteger();
        AtomicLong worstGap = new AtomicLong();
        Consumer<StreamFrame> countingSink = f -> {
            sent.incrementAndGet();
            sink.accept(f);
        };
        for (InetAddress addr : candidates) {
            try {
                streamVia(addr, host, port, path, request, countingSink, worstGap);
                String ip = addr.getHostAddress();
                if (worstGap.get() > STALL_MARK_MS && sent.get() > 1) {
                    // 节点可连可流但中段 hold：真机实测 GA 边缘节点存在「流一段→停顿数秒→
                    // 结束倾泻」形态。冷却该节点（粘住它会让后续请求继续不平滑），
                    // 下次请求优先其他节点，逐步收敛到稳定节点
                    badNodesUntil.put(ip, System.currentTimeMillis() + NODE_COOLDOWN_MS);
                    if (ip.equals(lastGoodIp.get())) {
                        lastGoodIp.compareAndSet(ip, null);
                    }
                    log.warn("[原生流] 节点 {} 中段停顿 {}ms（不平滑），冷却 {}ms",
                            ip, worstGap.get(), NODE_COOLDOWN_MS);
                } else {
                    lastGoodIp.set(ip);
                }
                return;
            } catch (UpstreamStreamException e) {
                // HTTP 状态错误/流中断：换节点重试同样会失败（认证/模型问题），直接抛出
                throw e;
            } catch (IOException e) {
                // 已投递过帧的流中途断开：重试会导致帧重复，不再换节点
                if (sent.get() > 0) {
                    throw new UpstreamStreamException("上游流式响应中断: " + e.getMessage(), e);
                }
                last = e;
                log.warn("[原生流] 节点 {} 首字节超时或连接失败({})，切换下一候选",
                        addr.getHostAddress(), e.getClass().getSimpleName());
            }
        }
        throw new UpstreamStreamException("上游流式连接失败(已尝试全部节点): "
                + (last == null ? "无可用地址" : last.getMessage()), last);
    }

    /**
     * 直连指定 IP 的 SSE 请求：SNI/Host/证书校验保持域名语义，
     * 首字节短超时识别坏节点（连上但不回数据），流开始后放宽读超时。
     */
    private void streamVia(InetAddress addr, String host, int port, String path,
                           StreamRequest request, Consumer<StreamFrame> sink,
                           AtomicLong worstGap) throws IOException {
        long firstByteTimeout = Math.max(5_000, Math.min(30_000, request.headersTimeout().toMillis()));
        SSLSocket socket = (SSLSocket)
                SSL_SOCKET_FACTORY.createSocket();
        try {
            socket.connect(new InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS);
            SSLParameters params = socket.getSSLParameters();
            params.setServerNames(List.of(new SNIHostName(host)));
            socket.setSSLParameters(params);
            socket.startHandshake();
            if (!verifyHostInCert(host, socket)) {
                throw new SSLException(
                        "证书主机名不匹配: " + addr.getHostAddress() + " → " + host);
            }
            socket.setSoTimeout((int) firstByteTimeout);

            String head = "POST " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + (port == 443 ? "" : ":" + port) + "\r\n"
                    + "Authorization: Bearer " + request.apiKey() + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Accept: text/event-stream\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "User-Agent: forge-admin/1.0\r\n"
                    + "Content-Length: " + request.body().getBytes(StandardCharsets.UTF_8).length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(request.body().getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream raw = new BufferedInputStream(socket.getInputStream());
            String statusLine = readRawLine(raw);
            if (statusLine == null || !statusLine.contains(" 200 ")) {
                throw new UpstreamStreamException(statusCodeOf(statusLine), drainSnippet(raw));
            }
            boolean chunked = false;
            String headerLine;
            while ((headerLine = readRawLine(raw)) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().contains("transfer-encoding")
                        && headerLine.toLowerCase().contains("chunked")) {
                    chunked = true;
                }
            }

            // 流开始后放宽读超时（帧间隔可达数秒）
            socket.setSoTimeout(READ_TIMEOUT_MS);
            InputStream body = chunked ? new DechunkingInputStream(raw) : raw;
            BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
            long deadline = System.currentTimeMillis() + MAX_STREAM_DURATION.toMillis();
            readFrames(reader, sink, deadline, worstGap);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭失败不影响主流程
            }
        }
    }

    /**
     * 证书 SAN 校验（显式实现）：直连 IP 时 JSSE 的端点识别锚定的是 IP 而非域名，
     * 默认 HostnameVerifier 对手工会话校验不可靠，故自实现 dNSName 精确/左通配匹配
     */
    private static boolean verifyHostInCert(String host, SSLSocket socket) {
        Certificate cert;
        try {
            cert = socket.getSession().getPeerCertificates()[0];
        } catch (SSLPeerUnverifiedException e) {
            return false;
        }
        if (!(cert instanceof X509Certificate x509)) {
            return false;
        }
        Collection<List<?>> sans;
        try {
            sans = x509.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            return false;
        }
        if (sans != null) {
            for (var san : sans) {
                // type 2 = dNSName
                if (san.size() >= 2 && Integer.valueOf(2).equals(san.get(0))
                        && san.get(1) instanceof String dns) {
                    if (hostnameMatches(dns.toLowerCase(), host.toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 精确匹配或左标签单段通配（*.bigmodel.cn 匹配 open.bigmodel.cn） */
    private static boolean hostnameMatches(String pattern, String host) {
        if (pattern.equals(host)) {
            return true;
        }
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            int dot = host.indexOf('.');
            return dot > 0 && host.substring(dot).equals(suffix);
        }
        return false;
    }

    /**
     * 候选节点排序：冷却中的节点最后；其余中上次成功 IP 优先；IPv4 次之
     */
    private List<InetAddress> candidateAddresses(String host) {
        InetAddress[] all;
        try {
            all = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new UpstreamStreamException("域名解析失败: " + host, e);
        }
        long now = System.currentTimeMillis();
        String good = lastGoodIp.get();
        List<InetAddress> list = new ArrayList<>(Arrays.asList(all));
        list.sort(Comparator
                .comparing((InetAddress a) -> {
                    Long until = badNodesUntil.get(a.getHostAddress());
                    return until != null && until > now;
                })
                .thenComparing(a -> !a.getHostAddress().equals(good))
                .thenComparing(a -> a.getAddress().length != 4));
        if (list.size() > MAX_NODE_ATTEMPTS) {
            // 冷却节点排在末尾被截掉时，保留它们参与下一轮（不清表）
            list = list.subList(0, MAX_NODE_ATTEMPTS);
        }
        return list;
    }

    /**
     * 原始流按行读（\n 或 \r\n 结尾，8KB 上限）；首行受首字节超时保护（坏节点在此暴露）
     */
    private static String readRawLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] bytes = buf.toByteArray();
                int end = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, end, StandardCharsets.UTF_8);
            }
            buf.write(c);
            if (buf.size() > 8192) {
                throw new IOException("响应行超长(>8KB)，非正常 HTTP 头");
            }
        }
        return buf.size() == 0 ? null : buf.toString(StandardCharsets.UTF_8);
    }

    private static int statusCodeOf(String statusLine) {
        if (statusLine == null) {
            return 0;
        }
        String[] parts = statusLine.split(" ");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    private static String drainSnippet(InputStream in) {
        try {
            return new String(in.readNBytes(512), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * chunked 传输解码：SSE 正文按块传输时逐块剥离 chunk 头（Connection: close 场景多数为
     * EOF 定界非 chunked，但 keep-alive 上游可能 chunked，两种都兼容）
     */
    static class DechunkingInputStream extends InputStream {
        private final InputStream in;
        private long remaining;
        private State state = State.SIZE;

        private enum State { SIZE, DATA, DATA_CRLF, DONE }

        DechunkingInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            while (true) {
                switch (state) {
                    case DONE -> {
                        return -1;
                    }
                    case SIZE -> {
                        String sizeLine = readRawLine(in);
                        if (sizeLine == null) {
                            state = State.DONE;
                            return -1;
                        }
                        if (sizeLine.isEmpty()) {
                            continue;
                        }
                        int semi = sizeLine.indexOf(';');
                        String hex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();
                        remaining = Long.parseLong(hex, 16);
                        state = remaining == 0 ? State.DONE : State.DATA;
                    }
                    case DATA -> {
                        if (remaining <= 0) {
                            state = State.DATA_CRLF;
                            continue;
                        }
                        int n = in.read(b, off, (int) Math.min(len, remaining));
                        if (n == -1) {
                            state = State.DONE;
                            return -1;
                        }
                        remaining -= n;
                        if (remaining == 0) {
                            state = State.DATA_CRLF;
                        }
                        return n;
                    }
                    case DATA_CRLF -> {
                        String tail = readRawLine(in);
                        if (tail == null) {
                            state = State.DONE;
                            return -1;
                        }
                        state = State.SIZE;
                    }
                }
            }
        }
    }

    /**
     * SSE 行解析：data 行 → 帧；usage 附着到 DONE 帧（上游无 [DONE] 以 EOF 收口）
     */
    void readFrames(BufferedReader reader, Consumer<StreamFrame> sink, long deadlineMillis) throws IOException {
        readFrames(reader, sink, deadlineMillis, null);
    }

    /**
     * 带帧间隔统计的读循环：worstGap 记录相邻数据行的最大间隔（节点中段 hold 判定依据），
     * 传 null 表示不统计
     */
    void readFrames(BufferedReader reader, Consumer<StreamFrame> sink, long deadlineMillis,
                    AtomicLong worstGap) throws IOException {
        Long promptTokens = null;
        Long completionTokens = null;
        String line;
        boolean done = false;
        long lastDataAt = 0L;
        while (!done && (line = reader.readLine()) != null) {
            if (System.currentTimeMillis() > deadlineMillis) {
                throw new UpstreamStreamException("上游流式响应超过最大时长限制，已中断", null);
            }
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isEmpty()) {
                continue;
            }
            if (worstGap != null) {
                long now = System.currentTimeMillis();
                if (lastDataAt > 0) {
                    worstGap.accumulateAndGet(now - lastDataAt, Math::max);
                }
                lastDataAt = now;
            }
            if ("[DONE]".equals(data)) {
                done = true;
                break;
            }
            JsonNode node;
            try {
                node = objectMapper.readTree(data);
            } catch (Exception e) {
                log.warn("[原生流] 无法解析的 SSE 行已跳过: {}", abbreviate(data));
                continue;
            }

            JsonNode usage = node.get("usage");
            if (usage != null && usage.isObject()) {
                promptTokens = longOrNull(usage, "prompt_tokens");
                completionTokens = longOrNull(usage, "completion_tokens");
            }

            JsonNode delta = node.path("choices").path(0).path("delta");
            if (delta.isMissingNode() || delta.isNull()) {
                // usage-only 尾块（choices 空数组）或异常帧：只累积 usage
                continue;
            }
            String reasoning = textOrNull(delta, "reasoning_content");
            if (reasoning != null) {
                sink.accept(StreamFrame.reasoning(reasoning));
            }
            String content = textOrNull(delta, "content");
            if (content != null) {
                sink.accept(StreamFrame.content(content));
            }
        }
        sink.accept(StreamFrame.done(promptTokens, completionTokens));
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String textOrNull(JsonNode delta, String field) {
        JsonNode value = delta.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isEmpty() ? null : text;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
