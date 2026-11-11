package com.forge.modules.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    /**
     * 建连+响应头超时（请求级）；流开始后的逐行间隔依赖上游持续输出，
     * 行间隔层面无读超时（残余风险，见 04 审查记录）
     */
    private static final Duration RESPONSE_HEADERS_TIMEOUT = Duration.ofSeconds(60);

    public record StreamRequest(String url, String apiKey, String body) {
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
    private final HttpClient httpClient;

    public OpenAiCompatStreamClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 流式调用：元素为解析后的帧，reasoning 与 content 增量分离透传
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
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(RESPONSE_HEADERS_TIMEOUT)
                .header("Authorization", "Bearer " + request.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response =
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (InputStream errorBody = response.body()) {
                throw new UpstreamStreamException(response.statusCode(), bodySnippet(errorBody));
            }
        }

        long deadline = System.currentTimeMillis() + MAX_STREAM_DURATION.toMillis();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            readFrames(reader, sink, deadline);
        }
    }

    /**
     * SSE 行解析：data 行 → 帧；usage 附着到 DONE 帧（上游无 [DONE] 以 EOF 收口）
     */
    void readFrames(BufferedReader reader, Consumer<StreamFrame> sink, long deadlineMillis) throws IOException {
        Long promptTokens = null;
        Long completionTokens = null;
        String line;
        boolean done = false;
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

    private String bodySnippet(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(512);
        String text = new String(bytes, StandardCharsets.UTF_8);
        return abbreviate(text);
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
