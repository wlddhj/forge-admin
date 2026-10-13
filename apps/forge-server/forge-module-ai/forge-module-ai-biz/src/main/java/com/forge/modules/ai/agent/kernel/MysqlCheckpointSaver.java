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

/**
 * MySQL checkpoint 持久化：state JSON 序列化入 ai_agent_checkpoint（含 nextNodeId，put 后框架会经
 * loadCheckpoints 重建对象，字段缺失会触发 NPE 校验）。threadId 格式约定 "run-{runId}"
 */
public class MysqlCheckpointSaver extends AbstractCheckpointSaver {

    private final AiAgentCheckpointMapper checkpointMapper;
    private final ObjectMapper objectMapper;
    private final long runId;

    public MysqlCheckpointSaver(AiAgentCheckpointMapper checkpointMapper, ObjectMapper objectMapper, long runId) {
        this.checkpointMapper = checkpointMapper;
        this.objectMapper = objectMapper;
        this.runId = runId;
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) {
        LinkedList<Checkpoint> checkpoints = new LinkedList<>();
        String threadId = threadId(config);
        var entities = checkpointMapper.selectList(new LambdaQueryWrapper<AiAgentCheckpoint>()
                .eq(AiAgentCheckpoint::getThreadId, threadId)
                .orderByAsc(AiAgentCheckpoint::getId));
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
        AiAgentCheckpoint entity = new AiAgentCheckpoint();
        entity.setRunId(runId);
        entity.setThreadId(threadId(config));
        entity.setCheckpointId(checkpoint.getId());
        entity.setNode(checkpoint.getNodeId());
        entity.setNextNodeId(checkpoint.getNextNodeId());
        entity.setState(toJson(checkpoint.getState()));
        checkpointMapper.insert(entity);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) {
        AiAgentCheckpoint entity = checkpointMapper.selectOne(new LambdaQueryWrapper<AiAgentCheckpoint>()
                .eq(AiAgentCheckpoint::getThreadId, threadId(config))
                .eq(AiAgentCheckpoint::getCheckpointId, checkpoint.getId())
                .last("LIMIT 1"));
        if (entity != null) {
            entity.setNode(checkpoint.getNodeId());
            entity.setNextNodeId(checkpoint.getNextNodeId());
            entity.setState(toJson(checkpoint.getState()));
            checkpointMapper.updateById(entity);
        } else {
            insertedCheckpoint(config, checkpoints, checkpoint);
        }
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                     String checkpointId) {
        checkpointMapper.delete(new LambdaQueryWrapper<AiAgentCheckpoint>()
                .eq(AiAgentCheckpoint::getThreadId, threadId(config)));
        return new Tag(threadId(config), checkpoints);
    }

    @Override
    protected Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                            Throwable error) {
        return releaseCheckpoints(config, checkpoints, null);
    }

    private final Map<String, Object> interruptions = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public <State extends org.bsc.langgraph4j.state.AgentState>
    CompletableFuture<org.bsc.langgraph4j.action.InterruptionMetadata<State>>
    registerInterruption(RunnableConfig config, org.bsc.langgraph4j.action.InterruptionMetadata<State> metadata) {
        interruptions.put(threadId(config), metadata);
        return CompletableFuture.completedFuture(metadata);
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
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException("checkpoint state 反序列化失败", e);
        }
    }

    private String toJson(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state == null ? Map.of() : state);
        } catch (Exception e) {
            throw new IllegalStateException("checkpoint state 序列化失败", e);
        }
    }
}
