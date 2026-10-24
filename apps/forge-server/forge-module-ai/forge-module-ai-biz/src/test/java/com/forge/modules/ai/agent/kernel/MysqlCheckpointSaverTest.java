package com.forge.modules.ai.agent.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.entity.AiAgentCheckpoint;
import com.forge.modules.ai.mapper.AiAgentCheckpointMapper;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MySQL 检查点持久化单测：upsert 单语句、节流间隔、flush 强制落最新、EXPERT_DONE 标记读写、
 * keepOnRelease 保留行（AC8）
 */
class MysqlCheckpointSaverTest {

    private AiAgentCheckpointMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RunnableConfig config;

    @BeforeEach
    void setUp() {
        mapper = mock(AiAgentCheckpointMapper.class);
        when(mapper.upsert(any(AiAgentCheckpoint.class))).thenReturn(1);
        config = RunnableConfig.builder().threadId("run-1").build();
    }

    @Test
    void 默认间隔逐检查点upsert落库() {
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(mapper, objectMapper, 1L);
        saver.insertedCheckpoint(config, new LinkedList<>(), checkpoint("c1"));
        saver.insertedCheckpoint(config, new LinkedList<>(), checkpoint("c2"));
        // upsert 单语句替代 selectOne+update 两跳
        verify(mapper, times(2)).upsert(any(AiAgentCheckpoint.class));
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void 间隔节流_跳过中间检查点_flush强制落最新() {
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(mapper, objectMapper, 1L, 2, false);
        saver.insertedCheckpoint(config, new LinkedList<>(), checkpoint("c1"));
        saver.insertedCheckpoint(config, new LinkedList<>(), checkpoint("c2"));
        saver.insertedCheckpoint(config, new LinkedList<>(), checkpoint("c3"));
        // 间隔 2：c1 计数 1 落、c2 计数 2 落、c3 计数 3 跳过 → 2 次
        verify(mapper, times(2)).upsert(any(AiAgentCheckpoint.class));

        ArgumentCaptor<AiAgentCheckpoint> captor = ArgumentCaptor.forClass(AiAgentCheckpoint.class);
        saver.flush(config);
        verify(mapper, times(3)).upsert(captor.capture());
        assertEquals("c3", captor.getValue().getCheckpointId());
    }

    @Test
    void release后flush不再落库() {
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(mapper, objectMapper, 1L);
        saver.insertedCheckpoint(config, new LinkedList<>(), checkpoint("c1"));
        saver.releaseCheckpoints(config, new LinkedList<>(), null);
        saver.flush(config);
        // 仅 c1 一次写入，flush 未追加
        verify(mapper, times(1)).upsert(any(AiAgentCheckpoint.class));
    }

    @Test
    void keepOnRelease的release不删行() {
        when(mapper.delete(any())).thenReturn(1);
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(mapper, objectMapper, 1L, 1, true);
        saver.releaseCheckpoints(config, new LinkedList<>(), null);
        verify(mapper, never()).delete(any());
    }

    @Test
    void 完成标记写入与读取() {
        when(mapper.selectOne(any())).thenAnswer(inv -> {
            AiAgentCheckpoint entity = new AiAgentCheckpoint();
            entity.setThreadId("t");
            entity.setCheckpointId(MysqlCheckpointSaver.DONE_MARKER_ID);
            entity.setState("{\"expertDone\":true,\"report\":{\"agent\":\"sql 专家\"}}");
            return entity;
        });
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(mapper, objectMapper, 1L);

        saver.markDone("t", Map.of("expertDone", true, "report", Map.of("agent", "sql 专家")));
        verify(mapper).upsert(any(AiAgentCheckpoint.class));

        var done = saver.loadDone("t");
        assertTrue(done.isPresent());
        assertEquals(Boolean.TRUE, done.get().get("expertDone"));
    }

    @Test
    void 无标记返回empty_线程存在性判定() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectCount(any())).thenReturn(0L, 3L);
        MysqlCheckpointSaver saver = new MysqlCheckpointSaver(mapper, objectMapper, 1L);
        assertTrue(saver.loadDone("t").isEmpty());
        assertFalse(saver.hasAnyCheckpoint("t"));
        assertTrue(saver.hasAnyCheckpoint("t"));
    }

    // ========== 工具 ==========

    private Checkpoint checkpoint(String id) {
        return Checkpoint.builder()
                .id(id)
                .nodeId("decision")
                .nextNodeId("tools")
                .state(Map.of("steps", 1))
                .build();
    }
}
