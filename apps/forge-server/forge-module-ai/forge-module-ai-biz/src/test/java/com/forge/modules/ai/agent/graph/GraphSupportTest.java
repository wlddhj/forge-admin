package com.forge.modules.ai.agent.graph;

import com.forge.modules.ai.agent.kernel.AgentGraphState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraphSupport 共享工具组单元测试（上帝类拆分第四批 T5）：
 * 两图四组重复方法的单一实现守护——尤其 describePendingCalls 的漂移收敛
 * （团队版曾丢失"决策信息缺失"分支）。
 */
class GraphSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("describePendingCalls：决策缺失分支（团队图漂移已收敛回单一实现）")
    void describePendingCalls_shouldReportMissingDecision() {
        String detail = GraphSupport.describePendingCalls(new AgentGraphState(Map.of()), objectMapper);
        assertEquals("（决策信息缺失）", detail);
    }

    @Test
    @DisplayName("describePendingCalls：空 calls 分支")
    void describePendingCalls_shouldReportEmptyCalls() {
        String detail = GraphSupport.describePendingCalls(
                new AgentGraphState(Map.of(AgentGraphState.DECISION, Map.of("calls", List.of()))), objectMapper);
        assertEquals("（无调用明细）", detail);
    }

    @Test
    @DisplayName("describePendingCalls：正常输出工具与参数（JSON 序列化）")
    void describePendingCalls_shouldRenderToolAndArgs() {
        Map<String, Object> decision = Map.of("calls", List.of(
                Map.of("tool", "sql_query", "args", Map.of("sql", "SELECT 1"))));
        String detail = GraphSupport.describePendingCalls(
                new AgentGraphState(Map.of(AgentGraphState.DECISION, decision)), objectMapper);
        assertTrue(detail.contains("工具: sql_query"));
        assertTrue(detail.contains("SELECT 1"));
    }

    @Test
    @DisplayName("extractContentDelta：[DONE]/null 返回 null，content 帧取值，error 帧抛业务异常")
    void extractContentDelta_shouldHandleFrames() {
        assertNull(GraphSupport.extractContentDelta(null, objectMapper));
        assertNull(GraphSupport.extractContentDelta("[DONE]", objectMapper));
        assertEquals("你好", GraphSupport.extractContentDelta("{\"content\":\"你好\"}", objectMapper));
        org.junit.jupiter.api.Assertions.assertThrows(com.forge.common.exception.BusinessException.class,
                () -> GraphSupport.extractContentDelta("{\"error\":true,\"message\":\"bad\"}", objectMapper));
    }

    @Test
    @DisplayName("summaryTimeoutMs/nullToZero 兜底口径")
    void timeoutsAndNulls() {
        assertEquals(60000L, GraphSupport.summaryTimeoutMs(null));
        assertEquals(60000L, GraphSupport.summaryTimeoutMs(-1));
        assertEquals(30000L, GraphSupport.summaryTimeoutMs(30000));
        assertEquals(0, GraphSupport.nullToZero(null));
        assertEquals(3, GraphSupport.nullToZero(3));
    }
}
