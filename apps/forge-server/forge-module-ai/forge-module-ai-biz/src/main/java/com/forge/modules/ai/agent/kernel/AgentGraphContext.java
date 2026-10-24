package com.forge.modules.ai.agent.kernel;

import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentTeam;
import com.forge.modules.ai.entity.AiAgentTeamMember;
import com.forge.modules.ai.entity.AiModelConfig;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 图构建与运行上下文：配置、步骤模型绑定、工具与事件回调；run 级附件缓存（成员索引/子图 saver 等
 * 整 run 复用的非序列化对象，不进 checkpoint state）
 */
@Data
public class AgentGraphContext {

    private final AiAgentConfig agentConfig;

    /** 节点名 → 模型配置（model_bindings 解析结果） */
    private final Map<String, AiModelConfig> nodeModels;

    private final ToolRegistry toolRegistry;

    private final Consumer<AgentEvents.AgentEvent> emitter;

    private final java.util.concurrent.atomic.AtomicInteger stepSeq;

    /** 团队协作运行时的团队配置（单 Agent 运行为 null） */
    private AiAgentTeam team;

    /** 团队成员快照（团队协作运行时） */
    private List<AiAgentTeamMember> teamMembers;

    /** run 级配置缓存（Agent/模型配置整 run 复用，消灭循环内查库） */
    private RunScopedCache runCache;

    /** runId（子图检查点 threadId 组成部分） */
    private Long runId;

    /** 发起用户（0/空=定时任务/系统触发，工具按全量语义执行） */
    private Long userId;

    /** 子图检查点 saver 工厂（threadKey → keepOnRelease 的子图 saver），由 Runner 装配 */
    private java.util.function.Function<String, MysqlCheckpointSaver> subSaverFactory;

    private final Map<String, Object> attachments = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T attachment(String key) {
        return (T) attachments.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> optionalAttachment(String key) {
        return (Optional<T>) Optional.ofNullable(attachments.get(key));
    }

    /** 附件缓存（整 run 复用对象的懒加载，如成员索引） */
    public Object computeAttachmentIfAbsent(String key, Supplier<?> loader) {
        return attachments.computeIfAbsent(key, k -> loader.get());
    }

    /** 子图 saver（同一 threadKey 返回同一实例，中断注册与标记写入共享） */
    public MysqlCheckpointSaver subSaver(String threadKey) {
        return subSavers().computeIfAbsent("saver:" + threadKey, k -> subSaverFactory.apply(threadKey));
    }

    @SuppressWarnings("unchecked")
    private Map<String, MysqlCheckpointSaver> subSavers() {
        return (Map<String, MysqlCheckpointSaver>) attachments.computeIfAbsent(
                "subSavers", k -> new ConcurrentHashMap<String, MysqlCheckpointSaver>());
    }

    public void emit(AgentEvents.Type type, String node, String name, Map<String, Object> payload) {
        emitter.accept(new AgentEvents.AgentEvent(type, node, name, payload));
    }

    public int nextSeq() {
        return stepSeq.incrementAndGet();
    }
}
