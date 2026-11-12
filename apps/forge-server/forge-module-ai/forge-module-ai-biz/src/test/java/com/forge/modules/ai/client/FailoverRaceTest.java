package com.forge.modules.ai.client;

import tools.jackson.databind.ObjectMapper;
import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.config.AiModuleConfig;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import com.forge.modules.system.service.SysConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 决策 failover 单测：健康预筛（主模型跳过/候选过滤/成功恢复/fail-open）+ 批内真·首成功
 * （慢失败候选不拖快成功）（AC2/AC3）
 *
 * 决策通路走原生 SSE 客户端，流桩按请求 URL（模型 apiEndpoint）区分，规避竞速下顺序消费桩的
 * 不确定性。
 */
@ExtendWith(MockitoExtension.class)
class FailoverRaceTest {

    @Mock
    private OpenAiChatModelFactory modelFactory;
    @Mock
    private AiModelConfigMapper modelConfigMapper;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private OpenAiCompatStreamClient streamClient;

    private AiModuleConfig aiModuleConfig;
    private ModelHealthCache healthCache;
    private SpringAiLlmClient client;
    private ExecutorService executor;

    private final AiModelConfig primary = model(1L, "主模型");
    private final AiModelConfig backup1 = model(2L, "慢失败备选");
    private final AiModelConfig backup2 = model(3L, "快成功备选");

    @BeforeEach
    void setUp() {
        aiModuleConfig = new AiModuleConfig();
        aiModuleConfig.getClient().setDecisionTimeout(5000);
        aiModuleConfig.getClient().setHealthCacheTtlMs(150); // 短 TTL 便于验证过期恢复
        aiModuleConfig.getClient().setFailoverParallelism(2);
        healthCache = new ModelHealthCache(new LocalCaches(), aiModuleConfig);
        executor = Executors.newFixedThreadPool(4);
        client = new SpringAiLlmClient(modelFactory,
                new ObjectMapper(), modelConfigMapper,
                sysConfigService, aiModuleConfig, executor, executor, healthCache,
                com.forge.modules.ai.config.TestAiMetrics.noop(), streamClient);
        lenient().when(sysConfigService.getConfigValueByKey("ai.model.fallback-enabled")).thenReturn("1");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 批内慢失败不拖快成功_真首成功() {
        // 主模型立即失败；备选1 挂起 1500ms 后失败；备选2 立即成功
        stubFrameError(primary);
        stubFrames(backup1,
                Mono.delay(java.time.Duration.ofMillis(1500))
                        .flatMap(v -> Mono.<StreamFrame>error(new RuntimeException("slow fail")))
                        .flux());
        stubFrames(backup2, Flux.just(StreamFrame.content("ok"), StreamFrame.done(null, null)));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup1, backup2));

        long start = System.currentTimeMillis();
        ChatResponse response = client.chatForDecision(request(), primary, null, null);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getSuccess()).isTrue();
        // 顺序 join 实现会等满慢失败的 1500ms;真首成功应远小于该值
        assertThat(elapsed).as("总时延应≈快成功,不受同批慢失败支配").isLessThan(1200L);
    }

    @Test
    void 负缓存候选被跳过() {
        healthCache.markFailed(backup1); // 慢失败备选近期已失败
        stubFrameError(primary);
        stubFrames(backup2, Flux.just(StreamFrame.content("ok"), StreamFrame.done(null, null)));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup1, backup2));

        ChatResponse response = client.chatForDecision(request(), primary, null, null);

        assertThat(response.getSuccess()).isTrue();
        // backup1 被健康预筛过滤:不进入原生流式调用
        verify(streamClient, never()).stream(argThat(req ->
                req != null && req.url().startsWith("http://llm2.fake")));
    }

    @Test
    void 主模型负缓存命中直接竞速() {
        healthCache.markFailed(primary);
        stubFrames(backup2, Flux.just(StreamFrame.content("ok"), StreamFrame.done(null, null)));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup2));

        ChatResponse response = client.chatForDecision(request(), primary, null, null);

        assertThat(response.getSuccess()).isTrue();
        verify(streamClient, never()).stream(argThat(req ->
                req != null && req.url().startsWith("http://llm1.fake")));
    }

    @Test
    void 主模型成功后负缓存清除() {
        // 第一轮主模型失败(标记负缓存)但备选成功;第二轮主模型恢复且不再被跳过
        stubFrameErrorThen(primary, StreamFrame.content("recovered"));
        stubFrames(backup2, Flux.just(StreamFrame.content("backup"), StreamFrame.done(null, null)));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup2));

        ChatResponse first = client.chatForDecision(request(), primary, null, null);
        assertThat(first.getSuccess()).isTrue();
        assertThat(healthCache.recentlyFailed(primary)).isTrue();

        // TTL 内第二次调用:主模型仍被跳过,继续由备用承接
        ChatResponse withinTtl = client.chatForDecision(request(), primary, null, null);
        assertThat(withinTtl.getSuccess()).isTrue();
        assertThat(withinTtl.getContent()).isEqualTo("backup");

        // TTL 过期后:主模型恢复参与并成功,负缓存清除
        try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        ChatResponse second = client.chatForDecision(request(), primary, null, null);
        assertThat(second.getSuccess()).isTrue();
        assertThat(second.getContent()).isEqualTo("recovered");
        assertThat(healthCache.recentlyFailed(primary)).isFalse();
    }

    @Test
    void 全被预筛过滤时回退尝试主模型() {
        healthCache.markFailed(primary);
        healthCache.markFailed(backup2);
        stubFrames(primary, Flux.just(StreamFrame.content("primary-recovered"), StreamFrame.done(null, null)));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup2));

        ChatResponse response = client.chatForDecision(request(), primary, null, null);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("primary-recovered");
    }

    @Test
    void 主模型被跳过且竞速全败_明确失败文案() {
        healthCache.markFailed(primary);
        stubFrameError(primary);
        stubFrameError(backup2);
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(backup2));

        ChatResponse response = client.chatForDecision(request(), primary, null, null);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("主模型近期失败已跳过");
    }

    // ========== 工具 ==========

    private AiModelConfig model(Long id, String name) {
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setModelName(name);
        config.setModelCode(name);
        config.setModelType("text");
        config.setStatus(1);
        config.setMaxTokens(4096);
        config.setApiEndpoint("http://llm" + id + ".fake/v4");
        config.setApiKey("sk-" + id);
        return config;
    }

    private ChatRequest request() {
        ChatRequest request = new ChatRequest();
        request.setContent("决策");
        return request;
    }

    private void stubFrames(AiModelConfig config, Flux<StreamFrame> frames) {
        lenient().when(streamClient.stream(argThat(req ->
                        req != null && req.url().startsWith("http://llm" + config.getId() + ".fake"))))
                .thenReturn(frames);
    }

    private void stubFrameError(AiModelConfig config) {
        stubFrames(config, Flux.error(new RuntimeException("Connection refused")));
    }

    private void stubFrameErrorThen(AiModelConfig config, StreamFrame recovered) {
        lenient().when(streamClient.stream(argThat(req ->
                        req != null && req.url().startsWith("http://llm" + config.getId() + ".fake"))))
                .thenReturn(Flux.error(new RuntimeException("Connection refused")))
                .thenReturn(Flux.just(recovered, StreamFrame.done(null, null)));
    }
}
