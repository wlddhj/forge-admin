package com.forge.modules.ai.agent.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.framework.localcache.LocalCaches;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.AgentProperties;
import com.forge.modules.ai.agent.kernel.AgentGraphContext;
import com.forge.modules.ai.agent.kernel.AgentGraphState;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.client.LlmClient;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 图片识别缓存单测：同图同模型二次执行零 LLM 调用、换模型/换图不命中、禁用直通（AC4）
 */
@ExtendWith(MockitoExtension.class)
class MultimodalCacheTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<AgentEvents.AgentEvent> events = new ArrayList<>();
    private final AtomicInteger llmCalls = new AtomicInteger();

    private AgentProperties properties;
    private MultimodalSupport support;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.setImageCacheTtlMinutes(60);
        support = new MultimodalSupport(llmClient, new DecisionJsonParser(objectMapper),
                properties, new LocalCaches(), com.forge.modules.ai.config.TestAiMetrics.noop());
        lenient().when(llmClient.chat(any(ChatRequest.class), any(AiModelConfig.class)))
                .thenAnswer(inv -> {
                    llmCalls.incrementAndGet();
                    ChatResponse response = new ChatResponse();
                    response.setSuccess(true);
                    response.setContent("识别结果-" + llmCalls.get());
                    return response;
                });
    }

    @Test
    void 同图同模型二次执行零LLM调用() throws Exception {
        run(images("http://a/1.png"), visionModel(10L));
        run(images("http://a/1.png"), visionModel(10L));
        assertEquals(1, llmCalls.get());
        // 第二次事件标注 cacheHit
        assertTrue(events.stream().anyMatch(e ->
                e.type() == AgentEvents.Type.STEP_END
                        && Boolean.TRUE.equals(e.payload().get("cacheHit"))));
    }

    @Test
    void 换模型或换图不命中() throws Exception {
        run(images("http://a/1.png"), visionModel(10L));
        run(images("http://a/1.png"), visionModel(11L)); // 换模型
        run(images("http://a/2.png"), visionModel(10L)); // 换图
        assertEquals(3, llmCalls.get());
    }

    @Test
    void 缓存禁用时直通() throws Exception {
        properties.setImageCacheTtlMinutes(0);
        support = new MultimodalSupport(llmClient, new DecisionJsonParser(objectMapper),
                properties, new LocalCaches(), com.forge.modules.ai.config.TestAiMetrics.noop());
        run(images("http://a/1.png"), visionModel(10L));
        run(images("http://a/1.png"), visionModel(10L));
        assertEquals(2, llmCalls.get());
    }

    // ========== 工具 ==========

    /** 直接以初始 state 调用节点动作（不经图编排） */
    private void run(List<String> images, AiModelConfig model) throws Exception {
        AgentGraphContext ctx = new AgentGraphContext(null, Map.of("multimodal", model),
                new ToolRegistry(List.of()), events::add, new java.util.concurrent.atomic.AtomicInteger());
        org.bsc.langgraph4j.action.AsyncNodeAction<AgentGraphState> action =
                support.node(ctx);
        AgentGraphState state = new AgentGraphState(Map.of(
                AgentGraphState.IMAGES, images));
        action.apply(state).get(); // 缓存命中与否由 llmCalls 调用数断言
    }

    private List<String> images(String... refs) {
        return List.of(refs);
    }

    private AiModelConfig visionModel(Long id) {
        AiModelConfig model = new AiModelConfig();
        model.setId(id);
        model.setModelName("vision-" + id);
        model.setModelCode("glm-4.5v");
        return model;
    }
}
