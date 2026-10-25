package com.forge.modules.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import com.forge.modules.ai.agent.kernel.AgentRunEventBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 恢复事件桥单测：delta 合并窗口 / 非 delta 即时保序 / error 兜底 / 推送失败静默 / 并发安全
 */
class AgentRunEventBridgeTest {

    private NotificationService notificationService;
    private AgentRunEventBridge bridge;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<NotificationMessage> sent = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        doAnswer((Answer<Void>) invocation -> {
            sent.add(invocation.getArgument(1));
            return null;
        }).when(notificationService).sendToUser(any(), any());
        bridge = new AgentRunEventBridge(notificationService, objectMapper);
    }

    private JsonNode frameOf(NotificationMessage message) throws Exception {
        assertEquals("AGENT_RUN_EVENT", message.getType());
        assertEquals(Long.valueOf(9L), message.getRelatedId());
        return objectMapper.readTree(message.getContent());
    }

    @Test
    void 窗口内增量合并为单帧() throws Exception {
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "decision", "工具决策", Map.of("delta", "你")));
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "decision", "工具决策", Map.of("delta", "好")));
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "decision", "工具决策", Map.of("delta", "呀")));
        emitter.close();

        assertEquals(1, sent.size());
        JsonNode frame = frameOf(sent.get(0));
        assertEquals("THOUGHT", frame.get("event").asText());
        assertEquals("decision", frame.get("node").asText());
        assertEquals("你好呀", frame.get("data").get("delta").asText());
    }

    @Test
    void 窗口到期由后续增量触发推出() throws Exception {
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.CONTENT, "summary", "总结回答", Map.of("delta", "第一段")));
        // 等 200ms 窗口到期，第二条增量到达时触发 flush 第一条
        Thread.sleep(250);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.CONTENT, "summary", "总结回答", Map.of("delta", "第二段")));
        emitter.close();

        assertEquals(2, sent.size());
        assertEquals("第一段", frameOf(sent.get(0)).get("data").get("delta").asText());
        assertEquals("第二段", frameOf(sent.get(1)).get("data").get("delta").asText());
    }

    @Test
    void 非增量事件即时推送且先冲缓冲保序() throws Exception {
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "decision", "工具决策", Map.of("delta", "思考中")));
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.TOOL_CALL, "tools", "sql_query", Map.of("sql", "select 1")));
        // 未 close 即已推出 2 帧（TOOL_CALL 即时 + 其前 flush 的合并 delta）
        assertEquals(2, sent.size());
        assertEquals("THOUGHT", frameOf(sent.get(0)).get("event").asText());
        JsonNode tool = frameOf(sent.get(1));
        assertEquals("TOOL_CALL", tool.get("event").asText());
        assertEquals("select 1", tool.get("data").get("sql").asText());
        emitter.close();
    }

    @Test
    void 推送失败静默不影响执行() {
        doThrow(new RuntimeException("ws down")).when(notificationService).sendToUser(any(), any());
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        assertDoesNotThrow(() -> {
            emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "decision", "工具决策", Map.of("delta", "x")));
            emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.FINAL, "summary", "总结回答", Map.of("answer", "done")));
            emitter.close();
        });
    }

    @Test
    void 异常兜底错误帧且其后事件丢弃() throws Exception {
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.CONTENT, "summary", "总结回答", Map.of("delta", "半截")));
        emitter.error(new IllegalStateException("模型超时"));
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.FINAL, "summary", "总结回答", Map.of("answer", "late")));
        emitter.close();

        assertEquals(2, sent.size());
        assertEquals("CONTENT", frameOf(sent.get(0)).get("event").asText());
        JsonNode error = frameOf(sent.get(1));
        assertEquals("ERROR", error.get("event").asText());
        assertEquals("模型超时", error.get("data").get("message").asText());
    }

    @Test
    void close幂等且其后事件为noOp() {
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        emitter.close();
        emitter.close();
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.FINAL, "summary", "总结回答", Map.of("answer", "x")));
        emitter.error(new RuntimeException("late"));
        assertEquals(0, sent.size());
    }

    @Test
    void 节点名含竖线不串键() throws Exception {
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "expert|A", "专家|甲", Map.of("delta", "x")));
        emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "expert", "A|专家|甲", Map.of("delta", "y")));
        emitter.close();

        assertEquals(2, sent.size());
        JsonNode first = frameOf(sent.get(0));
        JsonNode second = frameOf(sent.get(1));
        // 键不碰撞：两路增量各自归位（节点/名称原样还原，不因 | 拆错）
        assertEquals("expert|A", first.get("node").asText());
        assertEquals("专家|甲", first.get("name").asText());
        assertEquals("x", first.get("data").get("delta").asText());
        assertEquals("expert", second.get("node").asText());
        assertEquals("A|专家|甲", second.get("name").asText());
        assertEquals("y", second.get("data").get("delta").asText());
    }

    @Test
    void 并发增量无丢失() throws Exception {
        AgentRunEventBridge.Emitter emitter = bridge.bridge(9L, 9L);
        int threads = 4;
        int perThread = 50;
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int id = t;
            new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    emitter.accept(new AgentEvents.AgentEvent(AgentEvents.Type.THOUGHT, "expert-decide", "专家" + id,
                            Map.of("delta", String.valueOf(id))));
                }
                latch.countDown();
            }).start();
        }
        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        emitter.close();

        // 窗口滚动下帧数不确定，但同 key 增量总数必须一分不差（线程安全 + 无丢失）
        int totalChars = 0;
        for (NotificationMessage message : new ArrayList<>(sent)) {
            JsonNode frame = frameOf(message);
            assertEquals("THOUGHT", frame.get("event").asText());
            totalChars += frame.get("data").get("delta").asText().length();
        }
        assertEquals(threads * perThread, totalChars);
    }
}
