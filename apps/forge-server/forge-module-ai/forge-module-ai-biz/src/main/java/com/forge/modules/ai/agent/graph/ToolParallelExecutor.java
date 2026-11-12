package com.forge.modules.ai.agent.graph;

import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.kernel.AgentGraphContext;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.ai.agent.tools.ToolExecutionContext;
import com.forge.modules.ai.agent.tools.ToolRegistry;
import com.forge.modules.ai.agent.kernel.AgentGraphState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具并行执行骨架：单 Agent toolsNode 与团队 expertToolsNode 各持 ~60 行同构代码
 * （提交→快照租户→执行→记录→emit），差异仅白名单校验与 agentCode 透传——
 * 收敛为单一执行器（评审修复第四批 §4.2）；调用方保留各自前置校验。
 *
 * @author forge-admin
 */
@Component
public class ToolParallelExecutor {

    private final Executor toolExecutor;

    public ToolParallelExecutor(@Qualifier("toolExecutor") Executor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    /** 单条工具执行结果记录（与历史 TOOL_HISTORY 条目形态一致） */
    public record ToolRecord(String tool, Map<String, Object> args, Object result, String error, String status, long durationMs) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tool", tool);
            m.put("args", args);
            if (result != null) {
                m.put("result", result);
            }
            if (error != null) {
                m.put("error", error);
            }
            m.put("status", status);
            return m;
        }
    }

    /**
     * 并行执行一组工具调用，返回结果记录（顺序与入参一致）。
     *
     * @param ctx          图上下文（emit 事件）
     * @param nodeLabel    事件节点名（tools / expert_tools）
     * @param calls        决策 calls 列表（Map: tool/args）
     * @param context      工具执行上下文（会话用户/系统）
     * @param agentCode    团队专家编码（单 Agent 传 null）
     * @param agentName    团队专家名（单 Agent 传 null）
     * @param seqAllocator 事件序号分配器（ctx::nextSeq）
     * @param truncate     结果截断函数（decisionJsonParser::truncate）
     * @param waitMinutes  整组等待上限（分钟；兜底防外部工具悬挂——调用方原 orTimeout 语义收编于此）
     */
    public List<Map<String, Object>> runParallel(AgentGraphContext ctx, String nodeLabel, List<?> calls,
                                                 ToolExecutionContext context, String agentCode, String agentName,
                                                 IntSupplier seqAllocator,
                                                 UnaryOperator<String> truncate,
                                                 long waitMinutes) {
        long start = System.currentTimeMillis();
        List<CompletableFuture<ToolRecord>> futures = new ArrayList<>();
        for (Object raw : calls) {
            Map<?, ?> call = (Map<?, ?>) raw;
            String toolName = String.valueOf(call.get("tool"));
            @SuppressWarnings("unchecked")
            Map<String, Object> args = call.get("args") == null
                    ? Map.of() : (Map<String, Object>) call.get("args");
            int callSeq = seqAllocator.getAsInt();
            Map<String, Object> extra = agentCode == null
                    ? Map.of("seq", callSeq, "args", args)
                    : Map.of("seq", callSeq, "args", args, "agentCode", agentCode, "agentName", agentName);
            ctx.emit(AgentEvents.Type.TOOL_CALL, nodeLabel, toolName, extra);
            // 子线程不继承 ThreadLocal 租户上下文，工具执行(SqlTenantRewriter 等)依赖它——提交前快照传递
            Long tenantSnapshot = TenantContextHolder.getTenantId();
            futures.add(CompletableFuture.supplyAsync(() -> {
                if (tenantSnapshot != null) {
                    TenantContextHolder.setTenantId(tenantSnapshot);
                }
                try {
                    long toolStart = System.currentTimeMillis();
                    try {
                        Object result = ctx.getToolRegistry().execute(toolName, args, context);
                        String truncated = truncate.apply(String.valueOf(result));
                        ctx.emit(AgentEvents.Type.TOOL_RESULT, nodeLabel, toolName, Map.of(
                                "seq", callSeq, "status", "SUCCESS", "output", truncated,
                                "durationMs", System.currentTimeMillis() - toolStart));
                        return new ToolRecord(toolName, args, truncated, null, "SUCCESS",
                                System.currentTimeMillis() - toolStart);
                    } catch (Exception e) {
                        ctx.emit(AgentEvents.Type.TOOL_RESULT, nodeLabel, toolName, Map.of(
                                "seq", callSeq, "status", "FAILED", "error", String.valueOf(e.getMessage()),
                                "durationMs", System.currentTimeMillis() - toolStart));
                        return new ToolRecord(toolName, args, null, e.getMessage(), "FAILED",
                                System.currentTimeMillis() - toolStart);
                    }
                } finally {
                    if (tenantSnapshot != null) {
                        TenantContextHolder.clear();
                    }
                }
            }, toolExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(waitMinutes, java.util.concurrent.TimeUnit.MINUTES).join();
        List<Map<String, Object>> records = new ArrayList<>();
        for (CompletableFuture<ToolRecord> f : futures) {
            records.add(f.join().toMap());
        }
        return records;
    }
}
