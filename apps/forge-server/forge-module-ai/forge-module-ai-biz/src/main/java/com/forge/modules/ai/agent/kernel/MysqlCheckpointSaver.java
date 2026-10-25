package com.forge.modules.ai.agent.kernel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.ai.entity.AiAgentCheckpoint;
import com.forge.modules.ai.mapper.AiAgentCheckpointMapper;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;

import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MySQL checkpoint 持久化：state JSON 序列化入 ai_agent_checkpoint（含 nextNodeId，put 后框架会经
 * loadCheckpoints 重建对象，字段缺失会触发 NPE 校验）。threadId 格式约定 "run-{runId}"。
 * 写入策略：upsert 单语句；按间隔节流（writeInterval>1 时跳过中间检查点，中断/终态由 flush 强制补写
 * 最新检查点保证恢复点完整）；keepOnRelease=true 的子图 saver 在 END 后保留检查点行（EXPERT_DONE 恢复依据），
 * 清理由 AgentDataCleanupService 按 run_id 生命周期统一处理。
 */
public class MysqlCheckpointSaver extends AbstractCheckpointSaver {

    /**
     * 状态专用 mapper：不能用 Spring 全局 ObjectMapper——其注册的 LongSerializer 为防前端 JS 精度丢失
     * 会把 Long 写成字符串（agentId:1 → "agentId":"1"），检查点往返后类型改变，恢复时
     * dispatch.get("agentId") instanceof Number 不成立导致专家配置取不到。此处必须类型稳定往返。
     */
    private static final ObjectMapper STATE_MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 专家子图完成标记 checkpointId（loadCheckpoints 过滤，不参与框架恢复） */
    public static final String DONE_MARKER_ID = "expert-done";

    private final AiAgentCheckpointMapper checkpointMapper;
    private final long runId;
    private final int writeInterval;
    private final boolean keepOnRelease;

    private final AtomicInteger sinceLastWrite = new AtomicInteger();
    private volatile Checkpoint lastCheckpoint;
    private volatile boolean released;

    public MysqlCheckpointSaver(AiAgentCheckpointMapper checkpointMapper, long runId) {
        this(checkpointMapper, runId, 1, false);
    }

    public MysqlCheckpointSaver(AiAgentCheckpointMapper checkpointMapper,
                                long runId, int writeInterval, boolean keepOnRelease) {
        this.checkpointMapper = checkpointMapper;
        this.runId = runId;
        this.writeInterval = Math.max(1, writeInterval);
        this.keepOnRelease = keepOnRelease;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) {
        LinkedList<Checkpoint> checkpoints = new LinkedList<>();
        String threadId = threadId(config);
        // 降序：框架 getLast 取队头（peek），最新检查点必须在首位（升序会导致恢复从最早检查点重放）
        var entities = checkpointMapper.selectList(new LambdaQueryWrapper<AiAgentCheckpoint>()
                .eq(AiAgentCheckpoint::getThreadId, threadId)
                .ne(AiAgentCheckpoint::getCheckpointId, DONE_MARKER_ID)
                .orderByDesc(AiAgentCheckpoint::getId));
        for (AiAgentCheckpoint entity : entities) {
            checkpoints.add(Checkpoint.builder()
                    .id(entity.getCheckpointId())
                    .nodeId(entity.getNode() == null ? "" : entity.getNode())
                    .nextNodeId(entity.getNextNodeId() == null ? "" : entity.getNextNodeId())
                    .state(fromJson(entity.getState()))
                    .build());
        }
        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                      Checkpoint checkpoint) {
        lastCheckpoint = checkpoint;
        if (shouldWrite()) {
            persist(config, checkpoint);
        }
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) {
        lastCheckpoint = checkpoint;
        if (shouldWrite()) {
            persist(config, checkpoint);
        }
    }

    /**
     * 强制落最新检查点（中断/终态调用；已 release 的线程 no-op，避免正常结束后残留脏行）
     */
    public void flush(RunnableConfig config) {
        Checkpoint checkpoint = lastCheckpoint;
        if (checkpoint == null || released) {
            return;
        }
        persist(config, checkpoint);
        sinceLastWrite.set(0);
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                     String checkpointId) {
        released = true;
        if (!keepOnRelease) {
            checkpointMapper.delete(new LambdaQueryWrapper<AiAgentCheckpoint>()
                    .eq(AiAgentCheckpoint::getThreadId, threadId(config)));
        }
        return new Tag(threadId(config), checkpoints);
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                            Throwable error) {
        released = true;
        if (!keepOnRelease) {
            checkpointMapper.delete(new LambdaQueryWrapper<AiAgentCheckpoint>()
                    .eq(AiAgentCheckpoint::getThreadId, threadId(config)));
        }
        return new Tag(threadId(config), checkpoints);
    }

    // ========== 专家子图完成标记（并行分派恢复依据） ==========

    /** 写入/更新完成标记（state 需含汇报内容，供恢复时直接复用） */
    public void markDone(String threadId, Map<String, Object> state) {
        AiAgentCheckpoint entity = new AiAgentCheckpoint();
        entity.setRunId(runId);
        entity.setThreadId(threadId);
        entity.setCheckpointId(DONE_MARKER_ID);
        entity.setNode("");
        entity.setNextNodeId("");
        entity.setState(toJson(state));
        checkpointMapper.upsert(entity);
    }

    /** 读取完成标记 state（无标记返回 empty） */
    public Optional<Map<String, Object>> loadDone(String threadId) {
        AiAgentCheckpoint entity = checkpointMapper.selectOne(new LambdaQueryWrapper<AiAgentCheckpoint>()
                .eq(AiAgentCheckpoint::getThreadId, threadId)
                .eq(AiAgentCheckpoint::getCheckpointId, DONE_MARKER_ID)
                .last("LIMIT 1"));
        return entity == null || entity.getState() == null
                ? Optional.empty() : Optional.of(fromJson(entity.getState()));
    }

    /** 该线程是否存在任何检查点（含标记）——中断续跑 vs 全新启动判定 */
    public boolean hasAnyCheckpoint(String threadId) {
        return checkpointMapper.selectCount(new LambdaQueryWrapper<AiAgentCheckpoint>()
                .eq(AiAgentCheckpoint::getThreadId, threadId)) > 0;
    }

    /** 删除该线程全部检查点（含标记） */
    public void deleteThread(String threadId) {
        checkpointMapper.delete(new LambdaQueryWrapper<AiAgentCheckpoint>()
                .eq(AiAgentCheckpoint::getThreadId, threadId));
    }

    // ========== 内部 ==========

    private boolean shouldWrite() {
        if (released) {
            return false;
        }
        if (writeInterval <= 1) {
            return true;
        }
        int count = sinceLastWrite.incrementAndGet();
        // 首个检查点必落（保证恢复基线），此后每 writeInterval 个落一个
        return count == 1 || count % writeInterval == 0;
    }

    private void persist(RunnableConfig config, Checkpoint checkpoint) {
        AiAgentCheckpoint entity = new AiAgentCheckpoint();
        entity.setRunId(runId);
        entity.setThreadId(threadId(config));
        entity.setCheckpointId(checkpoint.getId());
        entity.setNode(checkpoint.getNodeId());
        entity.setNextNodeId(checkpoint.getNextNodeId());
        entity.setState(toJson(checkpoint.getState()));
        checkpointMapper.upsert(entity);
    }

    private final Map<String, Object> interruptions = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public <State extends org.bsc.langgraph4j.state.AgentState>
    CompletableFuture<org.bsc.langgraph4j.action.InterruptionMetadata<State>>
    registerInterruption(RunnableConfig config, org.bsc.langgraph4j.action.InterruptionMetadata<State> metadata) {
        interruptions.put(threadId(config), metadata);
        return CompletableFuture.completedFuture(metadata);
    }

    /**
     * 最近一次动态中断的原因（框架吞掉 GraphInterruptException 正常收流时的中断详情来源）
     */
    public Optional<String> lastInterruptReason(RunnableConfig config) {
        Object meta = interruptions.get(threadId(config));
        if (meta instanceof org.bsc.langgraph4j.action.InterruptionMetadata<?> metadata) {
            return metadata.reason();
        }
        return Optional.empty();
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer version) {
        return Optional.of(new Tag(threadId(config), version, loadQuietly(config)));
    }

    private LinkedList<Checkpoint> loadQuietly(RunnableConfig config) {
        try {
            return loadCheckpoints(config);
        } catch (Exception e) {
            return new LinkedList<>();
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return STATE_MAPPER.readValue(json,
                    STATE_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException("checkpoint state 反序列化失败", e);
        }
    }

    private String toJson(Map<String, Object> state) {
        try {
            return STATE_MAPPER.writeValueAsString(state == null ? Map.of() : state);
        } catch (Exception e) {
            throw new IllegalStateException("checkpoint state 序列化失败", e);
        }
    }
}
