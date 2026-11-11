package com.forge.modules.ai.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import com.forge.modules.system.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * LLM 客户端实现：Spring AI 直连上游 OpenAI 兼容端点。
 * 流式帧契约与原 Python 服务一致：{"content":"…"} 增量 → [DONE]；异常 {"error":true,"message":"…"}
 */
@Slf4j
@Component
public class SpringAiLlmClient implements LlmClient {

    public static final String DONE_FRAME = "[DONE]";

    private static final String FALLBACK_ENABLED_KEY = "ai.model.fallback-enabled";
    private static final int FALLBACK_MAX_CANDIDATES = 3;

    private final OpenAiChatModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final SysConfigService sysConfigService;
    private final AiModuleConfig aiModuleConfig;
    private final Executor agentExecutor;
    private final ModelHealthCache healthCache;
    private final com.forge.modules.ai.config.AiMetrics aiMetrics;
    private final OpenAiCompatStreamClient streamClient;

    public SpringAiLlmClient(OpenAiChatModelFactory modelFactory,
                             ObjectMapper objectMapper,
                             AiModelConfigMapper modelConfigMapper,
                             SysConfigService sysConfigService,
                             AiModuleConfig aiModuleConfig,
                             @Qualifier("agentExecutor") Executor agentExecutor,
                             ModelHealthCache healthCache,
                             com.forge.modules.ai.config.AiMetrics aiMetrics,
                             OpenAiCompatStreamClient streamClient) {
        this.modelFactory = modelFactory;
        this.objectMapper = objectMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.sysConfigService = sysConfigService;
        this.aiModuleConfig = aiModuleConfig;
        this.agentExecutor = agentExecutor;
        this.healthCache = healthCache;
        this.aiMetrics = aiMetrics;
        this.streamClient = streamClient;
    }

    @Override
    public ChatResponse chat(ChatRequest request, AiModelConfig modelConfig) {
        ChatResponse primary = doChat(request, modelConfig);
        if (Boolean.TRUE.equals(primary.getSuccess())) {
            return primary;
        }
        return failover(request, modelConfig, primary);
    }

    /**
     * 故障转移：主模型失败后按序尝试同类型备用模型（启用状态、排除自身、最多 3 个）。
     * 开关关闭/无备用/全部失败时返回主模型原始失败响应；任何兜底异常不放大故障。
     */
    private ChatResponse failover(ChatRequest request, AiModelConfig primary, ChatResponse primaryResponse) {
        try {
            if (!fallbackEnabled()) {
                return primaryResponse;
            }
            List<AiModelConfig> candidates = fallbackCandidates(primary);
            for (AiModelConfig candidate : candidates) {
                ChatResponse response = doChat(request, candidate);
                if (Boolean.TRUE.equals(response.getSuccess())) {
                    log.warn("LLM 故障转移: 主模型 [{}] 失败({})，切换 [{}] 成功",
                            primary.getModelName(), primaryResponse.getErrorMessage(), candidate.getModelName());
                    return response;
                }
                log.warn("LLM 故障转移: 备用模型 [{}] 失败({})", candidate.getModelName(), response.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("LLM 故障转移流程异常，返回主模型原始失败响应", e);
        }
        return primaryResponse;
    }

    /**
     * 开关读取：读取失败视为开启（fail-safe，可用性优先）
     */
    private boolean fallbackEnabled() {
        try {
            String value = sysConfigService.getConfigValueByKey(FALLBACK_ENABLED_KEY);
            return value == null || value.isBlank() || !("0".equals(value.trim()) || "false".equalsIgnoreCase(value.trim()));
        } catch (Exception e) {
            return true;
        }
    }

    private List<AiModelConfig> fallbackCandidates(AiModelConfig primary) {
        List<AiModelConfig> candidates = modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelType, primary.getModelType())
                .eq(AiModelConfig::getStatus, 1)
                .ne(AiModelConfig::getId, primary.getId())
                .orderByAsc(AiModelConfig::getId)
                .last("LIMIT " + FALLBACK_MAX_CANDIDATES));
        // 健康预筛：近期失败的候选不进入故障转移（零额外探测延迟，失败历史负缓存）
        return candidates.stream()
                .filter(candidate -> !healthCache.recentlyFailed(candidate))
                .toList();
    }

    private ChatResponse doChat(ChatRequest request, AiModelConfig modelConfig) {
        ChatResponse response = new ChatResponse();
        long start = System.currentTimeMillis();
        try {
            ChatModel chatModel = modelFactory.get(modelConfig);
            // 全限定名：与本项目 DTO ChatResponse 同名，import 冲突（规范允许的例外）
            org.springframework.ai.chat.model.ChatResponse aiResponse = chatModel.call(buildPrompt(request, modelConfig));

            response.setSuccess(true);
            response.setModelName(modelConfig.getModelName());
            response.setResponseTime(System.currentTimeMillis() - start);
            if (aiResponse != null) {
                if (aiResponse.getResult() != null && aiResponse.getResult().getOutput() != null) {
                    response.setContent(aiResponse.getResult().getOutput().getText());
                }
                Usage usage = aiResponse.getMetadata() != null ? aiResponse.getMetadata().getUsage() : null;
                if (usage != null) {
                    response.setInputTokens(usage.getPromptTokens());
                    response.setOutputTokens(usage.getCompletionTokens());
                }
            }
            // 空 content 且按 max_tokens 截断：思考类模型（GLM-4.x 等）把预算耗在 reasoning 上，
            // content 恒为空，按成功处理会让上层拿到"空内容"谜团，此处显式判失败给出根因
            if ((response.getContent() == null || response.getContent().isBlank())
                    && aiResponse != null && aiResponse.getResult() != null
                    && aiResponse.getResult().getMetadata() != null
                    && "LENGTH".equalsIgnoreCase(String.valueOf(aiResponse.getResult().getMetadata().getFinishReason()))) {
                response.setSuccess(false);
                response.setErrorMessage("输出被 max_tokens=" + modelConfig.getMaxTokens()
                        + " 截断（思考类模型推理耗尽预算），请调大 max_tokens 或换用非思考模型");
            }
        } catch (Exception e) {
            String friendly = friendlyError(e);
            log.error("LLM 非流式调用失败 [{}]: {}", modelConfig.getModelName(), friendly, e);
            response.setSuccess(false);
            response.setModelName(modelConfig.getModelName());
            response.setErrorMessage(friendly);
            response.setResponseTime(System.currentTimeMillis() - start);
        }
        recordHealth(modelConfig, response);
        aiMetrics.llmCall("chat", modelConfig.getModelName(),
                Boolean.TRUE.equals(response.getSuccess()), response.getResponseTime());
        return response;
    }

    /** 成败挂健康负缓存标记（failover 预筛依据） */
    private void recordHealth(AiModelConfig modelConfig, ChatResponse response) {
        if (Boolean.TRUE.equals(response.getSuccess())) {
            healthCache.markHealthy(modelConfig);
        } else {
            healthCache.markFailed(modelConfig);
        }
    }

    @Override
    public Flux<String> stream(ChatRequest request, AiModelConfig modelConfig) {
        Prompt prompt = buildPrompt(request, modelConfig);
        String body = toOpenAiStreamBody(prompt);
        if (body == null) {
            // 含图片等 media：原生通路仅支持纯文本，回退 Spring AI 流（无 reasoning，行为同现状）
            return springAiStreamFrames(prompt, modelConfig);
        }
        String url = chatCompletionsUrl(modelConfig.getApiEndpoint());
        return streamClient
                .stream(new OpenAiCompatStreamClient.StreamRequest(url, modelConfig.getApiKey(), body,
                        streamHeadersTimeout()))
                .mapNotNull(frame -> switch (frame.type()) {
                    case REASONING -> frame.text() == null || frame.text().isEmpty() ? null : reasoningFrame(frame.text());
                    case CONTENT -> frame.text() == null || frame.text().isEmpty() ? null : contentFrame(frame.text());
                    case DONE -> DONE_FRAME;
                })
                .onErrorResume(e -> {
                    String friendly = friendlyStreamError(e);
                    log.error("LLM 流式调用失败 [{}]: {}", modelConfig.getModelName(), friendly, e);
                    return Flux.just(errorFrame(friendly));
                });
    }

    /**
     * Spring AI 通路（media 回退用）：仅透传正文增量，不解析 reasoning
     */
    private Flux<String> springAiStreamFrames(Prompt prompt, AiModelConfig modelConfig) {
        ChatModel chatModel = modelFactory.get(modelConfig);
        return chatModel.stream(prompt)
                .mapNotNull(aiResponse -> {
                    if (aiResponse.getResult() == null || aiResponse.getResult().getOutput() == null) {
                        return null;
                    }
                    String text = aiResponse.getResult().getOutput().getText();
                    return text == null || text.isEmpty() ? null : contentFrame(text);
                })
                .concatWith(Flux.just(DONE_FRAME))
                .onErrorResume(e -> {
                    String friendly = friendlyError(e instanceof Exception ex ? ex : new RuntimeException(e));
                    log.error("LLM 流式调用失败 [{}]: {}", modelConfig.getModelName(), friendly, e);
                    return Flux.just(errorFrame(friendly));
                });
    }

    /**
     * 决策类调用：流式聚合 + decision-timeout 独立收紧 + 失败后备用模型并行竞速（真·首成功即采用）。
     * 主模型近期失败时跳过直接竞速（全被健康预筛过滤时 fail-open 仍尝试主模型）。
     * 文本增量实时回调 onDelta（决策节点转发 THOUGHT 事件），思考增量回调 onReasoning
     * （决策节点转发 REASONING 事件，null 表示不关心），超时/错误返回 success=false。
     */
    @Override
    public ChatResponse chatForDecision(ChatRequest request, AiModelConfig modelConfig, Consumer<String> onDelta,
                                        Consumer<String> onReasoning) {
        boolean skipPrimary = healthCache.recentlyFailed(modelConfig);
        if (skipPrimary) {
            log.info("主模型 [{}] 近期失败已标记，跳过直接竞速备用模型", modelConfig.getModelName());
        }
        ChatResponse primary = skipPrimary ? null : doDecisionChat(request, modelConfig, onDelta, onReasoning);
        if (primary != null && Boolean.TRUE.equals(primary.getSuccess())) {
            return primary;
        }
        return raceFailover(modelConfig, primary,
                candidate -> doDecisionChat(request, candidate, onDelta, onReasoning));
    }

    private ChatResponse doDecisionChat(ChatRequest request, AiModelConfig modelConfig,
                                        Consumer<String> onDelta, Consumer<String> onReasoning) {
        ChatResponse response = new ChatResponse();
        long start = System.currentTimeMillis();
        response.setModelName(modelConfig.getModelName());
        try {
            Prompt prompt = buildPrompt(request, modelConfig);
            long timeout = aiModuleConfig.getClient().getDecisionTimeout() == null
                    ? 45000L : aiModuleConfig.getClient().getDecisionTimeout();

            String body = toOpenAiStreamBody(prompt);
            if (body != null) {
                completeDecisionNative(modelConfig, body, timeout, onDelta, onReasoning, response);
            } else {
                // 含图片等 media：回退 Spring AI 通路（无 reasoning 回调）
                completeDecisionLegacy(modelConfig, prompt, timeout, onDelta, response);
            }
            response.setSuccess(true);
            response.setResponseTime(System.currentTimeMillis() - start);
        } catch (Exception e) {
            String friendly = friendlyError(e instanceof Exception ex ? ex : new RuntimeException(e));
            String message = String.valueOf(e.getMessage()).toLowerCase();
            boolean timeout = e instanceof TimeoutException
                    || (e.getCause() instanceof TimeoutException)
                    || message.contains("timeout");
            log.error("LLM 决策调用失败 [{}] timeout={}: {}", modelConfig.getModelName(), timeout, friendly);
            response.setSuccess(false);
            response.setErrorMessage(timeout
                    ? "决策模型响应超时（" + (aiModuleConfig.getClient().getDecisionTimeout() == null
                            ? 45000 : aiModuleConfig.getClient().getDecisionTimeout()) + "ms），已尝试备用模型"
                    : friendly);
            response.setResponseTime(System.currentTimeMillis() - start);
        }
        recordHealth(modelConfig, response);
        aiMetrics.llmCall("decision", modelConfig.getModelName(),
                Boolean.TRUE.equals(response.getSuccess()), response.getResponseTime());
        return response;
    }

    /**
     * 原生通路聚合：reasoning/content 增量分别回调，usage 取自 DONE 帧
     */
    private void completeDecisionNative(AiModelConfig modelConfig, String body, long timeout,
                                        Consumer<String> onDelta, Consumer<String> onReasoning,
                                        ChatResponse response) {
        StringBuilder content = new StringBuilder();
        AtomicReference<StreamFrame> doneRef = new AtomicReference<>();
        String url = chatCompletionsUrl(modelConfig.getApiEndpoint());
        streamClient
                .stream(new OpenAiCompatStreamClient.StreamRequest(url, modelConfig.getApiKey(), body,
                        streamHeadersTimeout()))
                .doOnNext(frame -> {
                    switch (frame.type()) {
                        case REASONING -> {
                            if (onReasoning != null) {
                                onReasoning.accept(frame.text());
                            }
                        }
                        case CONTENT -> {
                            content.append(frame.text());
                            if (onDelta != null) {
                                onDelta.accept(frame.text());
                            }
                        }
                        case DONE -> doneRef.set(frame);
                    }
                })
                .blockLast(Duration.ofMillis(timeout));

        response.setContent(content.toString());
        StreamFrame done = doneRef.get();
        if (done != null) {
            response.setInputTokens(done.promptTokens() == null ? null : done.promptTokens().intValue());
            response.setOutputTokens(done.completionTokens() == null ? null : done.completionTokens().intValue());
        }
    }

    /**
     * Spring AI 通路聚合（media 回退用）：MessageAggregator 聚合流（含末块 usage），doOnNext 透传文本增量
     */
    private void completeDecisionLegacy(AiModelConfig modelConfig, Prompt prompt, long timeout,
                                        Consumer<String> onDelta, ChatResponse response) {
        ChatModel chatModel = modelFactory.get(modelConfig);
        StringBuilder content = new StringBuilder();
        AtomicReference<org.springframework.ai.chat.model.ChatResponse> aggregated =
                new AtomicReference<>();
        MessageAggregator aggregator = new MessageAggregator();
        Flux<org.springframework.ai.chat.model.ChatResponse> withDelta =
                chatModel.stream(prompt).doOnNext(chunk -> {
                    String delta = chunk.getResult() == null || chunk.getResult().getOutput() == null
                            ? null : chunk.getResult().getOutput().getText();
                    if (delta != null && !delta.isEmpty()) {
                        content.append(delta);
                        if (onDelta != null) {
                            onDelta.accept(delta);
                        }
                    }
                });
        org.springframework.ai.chat.model.ChatResponse aiResponse =
                aggregator.aggregate(withDelta, aggregated::set)
                        .blockLast(Duration.ofMillis(timeout));

        String text = content.length() > 0 ? content.toString()
                : (aiResponse != null && aiResponse.getResult() != null && aiResponse.getResult().getOutput() != null
                        ? aiResponse.getResult().getOutput().getText() : "");
        response.setContent(text == null ? "" : text);
        org.springframework.ai.chat.model.ChatResponse usageSource =
                aggregated.get() != null ? aggregated.get() : aiResponse;
        if (usageSource != null && usageSource.getMetadata() != null
                && usageSource.getMetadata().getUsage() != null) {
            response.setInputTokens(usageSource.getMetadata().getUsage().getPromptTokens());
            response.setOutputTokens(usageSource.getMetadata().getUsage().getCompletionTokens());
        }
    }

    /**
     * 备用模型并行竞速：按 failover-parallelism 分批并发调用，批内总时延 = 最慢者（替代串行求和）；
     * 任一成功即返回，全败进入下一批；开关关闭/无候选/全败返回主模型原始失败。
     */
    private ChatResponse raceFailover(AiModelConfig primary, ChatResponse primaryResponse,
                                      Function<AiModelConfig, ChatResponse> attempt) {
        try {
            if (!fallbackEnabled()) {
                return finalResponse(primary, primaryResponse);
            }
            // fail-open：主模型被跳过且候选全部被健康预筛过滤时，仍直接尝试主模型一次（可用性优先）
            List<AiModelConfig> candidates = fallbackCandidates(primary);
            if (candidates.isEmpty() && primaryResponse == null) {
                log.info("决策故障转移: 无可用备用候选（健康预筛后为空），fail-open 重试主模型 [{}]", primary.getModelName());
                return attempt.apply(primary);
            }
            int parallelism = Math.max(1, aiModuleConfig.getClient().getFailoverParallelism() == null
                    ? 1 : aiModuleConfig.getClient().getFailoverParallelism());
            for (int start = 0; start < candidates.size(); start += parallelism) {
                List<AiModelConfig> batch = candidates.subList(start,
                        Math.min(candidates.size(), start + parallelism));
                ChatResponse outcome = raceBatch(primary, batch, attempt);
                if (outcome != null) {
                    return outcome;
                }
            }
        } catch (Exception e) {
            log.warn("LLM 决策故障转移流程异常，返回主模型原始失败响应", e);
        }
        aiMetrics.failover("exhausted");
        return finalResponse(primary, primaryResponse);
    }

    /**
     * 批内真·首成功竞速：任一候选成功即刻完成（不再按提交顺序 join，慢速候选不拖快成功）；
     * 全部失败返回 null（进入下一批）。其余未完成 future 不再等待（结果丢弃，竞速成本已接受）。
     */
    private ChatResponse raceBatch(AiModelConfig primary, List<AiModelConfig> batch,
                                   Function<AiModelConfig, ChatResponse> attempt) {
        CompletableFuture<ChatResponse> firstSuccess = new CompletableFuture<>();
        AtomicInteger remaining =
                new AtomicInteger(batch.size());
        for (AiModelConfig candidate : batch) {
            CompletableFuture.supplyAsync(() -> attempt.apply(candidate), agentExecutor)
                    .whenComplete((response, error) -> {
                        if (error == null && response != null && Boolean.TRUE.equals(response.getSuccess())) {
                            firstSuccess.complete(response);
                        } else if (remaining.decrementAndGet() == 0 && !firstSuccess.isDone()) {
                            firstSuccess.complete(null);
                        }
                    });
        }
        ChatResponse outcome = firstSuccess.join();
        if (outcome != null) {
            log.warn("LLM 决策故障转移: 主模型 [{}] 失败，竞速切换成功", primary.getModelName());
            aiMetrics.failover("switched");
        } else {
            log.warn("LLM 决策故障转移: 批次全部失败 {}",
                    batch.stream().map(AiModelConfig::getModelName).toList());
        }
        return outcome;
    }

    /** 兜底响应：主模型被跳过（primaryResponse=null）时给出明确的预筛失败文案 */
    private ChatResponse finalResponse(AiModelConfig primary, ChatResponse primaryResponse) {
        if (primaryResponse != null) {
            return primaryResponse;
        }
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setModelName(primary.getModelName());
        response.setErrorMessage("主模型近期失败已跳过，且备用模型均不可用（健康预筛过滤或全部失败），请稍后重试");
        return response;
    }

    // ========== Prompt 构建 ==========

    private Prompt buildPrompt(ChatRequest request, AiModelConfig modelConfig) {
        List<Message> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatRequest.MessageItem item : request.getMessages()) {
                if (item.getContent() == null || item.getContent().isEmpty()) {
                    continue;
                }
                if ("assistant".equals(item.getRole())) {
                    messages.add(new AssistantMessage(item.getContent()));
                } else if ("system".equals(item.getRole())) {
                    messages.add(new SystemMessage(item.getContent()));
                } else {
                    messages.add(userMessage(item.getContent(), item.getImages()));
                }
            }
        }
        messages.add(userMessage(request.getContent(), request.getImages()));

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(modelConfig.getModelCode());
        if (request.getTemperature() != null) {
            options.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            options.maxTokens(request.getMaxTokens());
        }
        return new Prompt(messages, options.build());
    }

    private UserMessage userMessage(String text, List<String> images) {
        if (images == null || images.isEmpty()) {
            return new UserMessage(text);
        }
        List<Media> mediaList = new ArrayList<>();
        for (String image : images) {
            if (image == null || image.isBlank()) {
                continue;
            }
            Media.Builder builder = Media.builder()
                    .mimeType(guessMimeType(image));
            if (image.startsWith("http://") || image.startsWith("https://")) {
                try {
                    builder.data(new java.net.URL(image));
                } catch (java.net.MalformedURLException e) {
                    throw new IllegalArgumentException("图片 URL 非法: " + image, e);
                }
            } else {
                builder.data(image);
            }
            mediaList.add(builder.build());
        }
        return UserMessage.builder().text(text).media(mediaList).build();
    }

    private org.springframework.util.MimeType guessMimeType(String image) {
        String lower = image.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.contains("/jpg")
                || lower.contains("image/jpeg")) {
            return org.springframework.util.MimeTypeUtils.IMAGE_JPEG;
        }
        if (lower.endsWith(".gif") || lower.contains("image/gif")) {
            return org.springframework.util.MimeTypeUtils.IMAGE_GIF;
        }
        if (lower.endsWith(".webp") || lower.contains("image/webp")) {
            return org.springframework.util.MimeTypeUtils.parseMimeType("image/webp");
        }
        return org.springframework.util.MimeTypeUtils.IMAGE_PNG;
    }

    // ========== SSE 帧（Jackson 序列化保证换行等字符正确转义，等价 Python json.dumps） ==========

    private String contentFrame(String text) {
        return toJson(Map.of("content", text));
    }

    /**
     * 思考增量帧（新增，向后兼容：旧前端 JSON.parse 后无处理逻辑自动忽略）
     */
    private String reasoningFrame(String text) {
        return toJson(Map.of("reasoning", text));
    }

    private String errorFrame(String message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("error", true);
        frame.put("message", message);
        return toJson(frame);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            return "{\"error\":true,\"message\":\"响应序列化失败\"}";
        }
    }

    // ========== 原生 OpenAI 兼容通路（reasoning 透传） ==========

    /**
     * 将 Prompt 序列化为 chat/completions 请求体；含 media 或未支持消息类型返回 null（调用方回退 Spring AI 通路）。
     * 消息与温度/上限均取自 buildPrompt 产物，保证与旧通路请求语义一致。
     */
    private String toOpenAiStreamBody(Prompt prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            if (message instanceof org.springframework.ai.chat.messages.UserMessage userMessage
                    && userMessage.getMedia() != null && !userMessage.getMedia().isEmpty()) {
                return null;
            }
            Map<String, String> item = new LinkedHashMap<>();
            if (message instanceof org.springframework.ai.chat.messages.SystemMessage systemMessage) {
                item.put("role", "system");
                item.put("content", systemMessage.getText());
            } else if (message instanceof AssistantMessage assistantMessage) {
                item.put("role", "assistant");
                item.put("content", assistantMessage.getText());
            } else if (message instanceof org.springframework.ai.chat.messages.UserMessage userMessage) {
                item.put("role", "user");
                item.put("content", userMessage.getText());
            } else {
                return null;
            }
            if (item.get("content") == null) {
                return null;
            }
            messages.add(item);
        }
        if (messages.isEmpty()) {
            return null;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelCodeOf(prompt));
        body.put("messages", messages);
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        if (prompt.getOptions() instanceof OpenAiChatOptions options) {
            if (options.getTemperature() != null) {
                body.put("temperature", options.getTemperature());
            }
            if (options.getMaxTokens() != null) {
                body.put("max_tokens", options.getMaxTokens());
            }
        }
        return toJson(body);
    }

    private String modelCodeOf(Prompt prompt) {
        if (prompt.getOptions() instanceof OpenAiChatOptions options && options.getModel() != null) {
            return options.getModel();
        }
        throw new IllegalArgumentException("模型编码缺失，无法构造原生流式请求");
    }

    /**
     * apiEndpoint 对齐 Spring AI 约定（baseUrl + /chat/completions）。
     * 与 OpenAiChatModelFactory#normalizeBaseUrl 语义一致：存量数据可能存了完整
     * completions URL，先截去路径后缀再统一拼一次，避免双重后缀 404。
     */
    private String chatCompletionsUrl(String apiEndpoint) {
        String base = apiEndpoint == null ? "" : apiEndpoint.trim();
        String completionsPath = "/chat/completions";
        if (base.endsWith(completionsPath)) {
            base = base.substring(0, base.length() - completionsPath.length());
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + completionsPath;
    }

    /**
     * 原生流式「建连+响应头」超时：默认 120s（上游思考模型按 key 排队时响应头会迟到，
     * 实测 26~60s+ 可放行，60s 会误杀）；决策流另有 decision-timeout 独立收紧不受影响
     */
    private Duration streamHeadersTimeout() {
        Integer timeout = aiModuleConfig.getClient().getStreamHeadersTimeout();
        return Duration.ofMillis(timeout == null || timeout <= 0 ? 120000L : timeout);
    }

    private String friendlyStreamError(Throwable e) {
        if (e instanceof OpenAiCompatStreamClient.UpstreamStreamException upstream) {
            // 请求级超时（含上游排队等待）：status=0 且 cause 为 HttpTimeoutException
            Throwable cause = upstream.getCause();
            if (upstream.getStatusCode() == 0 && cause instanceof java.net.http.HttpTimeoutException) {
                return "上游模型排队超时（等待响应头超限），请稍后重试或更换模型";
            }
            return switch (upstream.getStatusCode()) {
                case 401, 403 -> "模型 API 密钥无效或无权限，请检查模型配置";
                case 429 -> "上游模型服务请求过于频繁，请稍后重试";
                case 504 -> "上游模型服务响应超时，请稍后重试";
                case 0 -> "上游模型连接失败: " + e.getMessage();
                default -> "上游模型服务内部错误(" + upstream.getStatusCode() + ")，请稍后重试";
            };
        }
        return friendlyError(e instanceof Exception ex ? ex : new RuntimeException(e));
    }

    // ========== 错误文案（沿用原 PythonServiceErrorHandler 语义） ==========

    private String friendlyError(Exception e) {
        if (e instanceof WebClientResponseException responseException) {
            return switch (responseException.getStatusCode().value()) {
                case 401, 403 -> "模型 API 密钥无效或无权限，请检查模型配置";
                case 429 -> "上游模型服务请求过于频繁，请稍后重试";
                case 504 -> "上游模型服务响应超时，请稍后重试";
                case 500, 502, 503 -> "上游模型服务内部错误，请稍后重试";
                default -> "请求上游模型失败: " + responseException.getStatusCode().value();
            };
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("Connection refused") || message.contains("ConnectException")) {
            return "无法连接模型服务端点，请检查模型配置的 API 端点";
        }
        if (message.contains("TimeoutException") || message.contains("timeout")
                || message.contains("ReadTimeout") || message.contains("responseTimeout")) {
            return "模型服务响应超时，请稍后重试";
        }
        return "调用模型服务失败: " + shorten(message);
    }

    private String shorten(String message) {
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }
}
