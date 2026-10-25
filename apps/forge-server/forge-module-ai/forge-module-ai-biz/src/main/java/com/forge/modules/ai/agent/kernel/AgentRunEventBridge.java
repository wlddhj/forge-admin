package com.forge.modules.ai.agent.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.framework.web.websocket.NotificationMessage;
import com.forge.framework.web.websocket.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.forge.modules.ai.agent.AgentEvents;

/**
 * 审批恢复执行的事件桥：把图执行事件经 WebSocket（AGENT_RUN_EVENT）推给发起用户，
 * 前端对话页渐进渲染，替代旧行为（恢复期间事件进空消费器、界面静默数十秒）。
 *
 * THOUGHT/CONTENT 为逐 token 增量，直推会打爆 STOMP：按 (type,node,name) 缓冲、
 * 200ms 窗口合并；其余事件即时推（推前先 flush 缓冲保序）。
 * 推送失败仅 warn（与既有 notify* 风格一致），绝不影响图执行主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunEventBridge {

    /** delta 合并窗口（毫秒）：连续增量流至多 5 帧/秒 */
    static final long FLUSH_INTERVAL_MS = 200;

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 为一次恢复执行创建有状态事件发射器（线程安全：团队图并行专家多线程 emit）。
     * 用毕必须 close()（finally 中），漏尾 delta 会丢失。
     */
    public Emitter bridge(Long userId, Long runId) {
        return new Emitter(userId, runId);
    }

    /** delta 缓冲键：node/name 来自用户配置的专家名等自由文本，不可用分隔符拼接（防 | 碰撞） */
    private record FrameKey(AgentEvents.Type type, String node, String name) {
    }

    public class Emitter implements Consumer<AgentEvents.AgentEvent> {

        private final Long userId;
        private final Long runId;
        /** delta 缓冲：FrameKey → 增量拼接，保插入序 */
        private final LinkedHashMap<FrameKey, StringBuilder> pending = new LinkedHashMap<>();
        private long lastFlush = System.currentTimeMillis();
        private boolean closed;

        private Emitter(Long userId, Long runId) {
            this.userId = userId;
            this.runId = runId;
        }

        @Override
        public synchronized void accept(AgentEvents.AgentEvent event) {
            if (closed || event == null) {
                return;
            }
            if (isDelta(event)) {
                long now = System.currentTimeMillis();
                if (now - lastFlush >= FLUSH_INTERVAL_MS && !pending.isEmpty()) {
                    flushPending();
                }
                pending.computeIfAbsent(new FrameKey(event.type(), event.node(), event.name()),
                                k -> new StringBuilder())
                        .append(String.valueOf(event.payload().get("delta")));
                return;
            }
            flushPending();
            send(event.type().name(), event.node(), event.name(), event.payload());
        }

        /** 恢复线程崩溃的兜底帧：前端气泡立即转 FAILED（轮询仍会回填终态） */
        public synchronized void error(Throwable e) {
            if (closed) {
                return;
            }
            flushPending();
            send("ERROR", null, "run",
                    Map.of("message", e.getMessage() == null ? "恢复执行失败" : e.getMessage()));
            closed = true;
        }

        /** 收尾：推出缓冲中的尾部增量（幂等，close 后 accept/error 均为 no-op） */
        public synchronized void close() {
            if (closed) {
                return;
            }
            flushPending();
            closed = true;
        }

        private boolean isDelta(AgentEvents.AgentEvent event) {
            return event.payload() != null && event.payload().containsKey("delta")
                    && (event.type() == AgentEvents.Type.THOUGHT || event.type() == AgentEvents.Type.CONTENT);
        }

        private void flushPending() {
            if (pending.isEmpty()) {
                return;
            }
            List<Map.Entry<FrameKey, StringBuilder>> snapshot = new ArrayList<>(pending.entrySet());
            pending.clear();
            lastFlush = System.currentTimeMillis();
            for (Map.Entry<FrameKey, StringBuilder> entry : snapshot) {
                String delta = entry.getValue().toString();
                send(entry.getKey().type().name(), entry.getKey().node(), entry.getKey().name(),
                        Map.of("delta", delta));
            }
        }

        private void send(String event, String node, String name, Map<String, Object> payload) {
            try {
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("event", event);
                frame.put("node", node);
                frame.put("name", name);
                frame.put("data", payload);
                NotificationMessage message = new NotificationMessage();
                message.setType("AGENT_RUN_EVENT");
                message.setTitle("Agent 运行 #" + runId + " 事件");
                message.setContent(objectMapper.writeValueAsString(frame));
                message.setRelatedId(runId);
                message.setTimestamp(System.currentTimeMillis());
                notificationService.sendToUser(userId, message);
            } catch (Exception e) {
                log.warn("Agent 恢复事件推送失败 runId={}: {}", runId, e.getMessage());
            }
        }
    }
}
