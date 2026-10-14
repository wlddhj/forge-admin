package com.forge.modules.ai.agent.lifecycle;

import com.forge.framework.tenant.core.aop.TenantIgnore;
import com.forge.modules.ai.dto.response.CleanupResultResponse;
import com.forge.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Agent 运行与对话数据生命周期清理服务。
 *
 * 按 sys_config 保留期对 run/step/todo/checkpoint/conversation/message 六表分批物理删除：
 * - step/checkpoint 按自身 create_time 超期删（step 大字段支持更短保留期）
 * - run 仅删已完结（SUCCEEDED/FAILED/CANCELLED），进行中/待审批保护
 * - todo 仅删非 PENDING，待处理保护
 * - 对话两表（ai_chat_conversation/ai_chat_message）按各自 create_time 超期删，消息附带孤儿清理
 * - 各表附带孤儿清理（关联主表已不存在的记录）
 * - 每表独立容错：配置非法或单表失败不影响其他表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDataCleanupService {

    private static final int MAX_BATCHES_PER_QUERY = 10_000;

    private final AiDataCleanupMapper cleanupMapper;
    private final SysConfigService configService;

    @TenantIgnore
    public CleanupResultResponse cleanupAll() {
        long start = System.currentTimeMillis();
        CleanupResultResponse result = new CleanupResultResponse();
        result.setRunDeleted(cleanupTable("run", "ai.agent.retention.run-days", 30, cutoff ->
                batchLoop(() -> cleanupMapper.selectExpiredRunIds(cutoff), cleanupMapper::deleteRunByIds)));
        result.setStepDeleted(cleanupTable("step", "ai.agent.retention.step-days", 30, cutoff ->
                batchLoop(() -> cleanupMapper.selectExpiredStepIds(cutoff), cleanupMapper::deleteStepByIds)
                        + batchLoop(cleanupMapper::selectOrphanStepIds, cleanupMapper::deleteStepByIds)));
        result.setTodoDeleted(cleanupTable("todo", "ai.agent.retention.todo-days", 30, cutoff ->
                batchLoop(() -> cleanupMapper.selectExpiredTodoIds(cutoff), cleanupMapper::deleteTodoByIds)
                        + batchLoop(cleanupMapper::selectOrphanTodoIds, cleanupMapper::deleteTodoByIds)));
        result.setCheckpointDeleted(cleanupTable("checkpoint", "ai.agent.retention.checkpoint-days", 7, cutoff ->
                batchLoop(() -> cleanupMapper.selectExpiredCheckpointIds(cutoff), cleanupMapper::deleteCheckpointByIds)
                        + batchLoop(cleanupMapper::selectOrphanCheckpointIds, cleanupMapper::deleteCheckpointByIds)));
        result.setConversationDeleted(cleanupTable("conversation", "ai.chat.retention.conversation-days", 90, cutoff ->
                batchLoop(() -> cleanupMapper.selectExpiredConversationIds(cutoff), cleanupMapper::deleteConversationByIds)));
        result.setMessageDeleted(cleanupTable("message", "ai.chat.retention.message-days", 90, cutoff ->
                batchLoop(() -> cleanupMapper.selectExpiredMessageIds(cutoff), cleanupMapper::deleteMessageByIds)
                        + batchLoop(cleanupMapper::selectOrphanMessageIds, cleanupMapper::deleteMessageByIds)));
        result.setDurationMs(System.currentTimeMillis() - start);
        log.info("Agent 运行与对话数据清理完成: run={}, step={}, todo={}, checkpoint={}, conversation={}, message={}, 耗时={}ms",
                result.getRunDeleted(), result.getStepDeleted(), result.getTodoDeleted(),
                result.getCheckpointDeleted(), result.getConversationDeleted(), result.getMessageDeleted(),
                result.getDurationMs());
        return result;
    }

    /**
     * 单表清理容错包装：配置非法或清理异常记日志返回 0，不中断其他表
     */
    private long cleanupTable(String tableName, String configKey, int defaultDays,
                              Function<LocalDateTime, Long> cleaner) {
        try {
            LocalDateTime cutoff = daysCutoff(configKey, defaultDays);
            if (cutoff == null) {
                log.warn("Agent 数据清理: {} 保留期配置非法，跳过", tableName);
                return 0;
            }
            return cleaner.apply(cutoff);
        } catch (Exception e) {
            log.error("Agent 数据清理: {} 清理失败", tableName, e);
            return 0;
        }
    }

    /**
     * 分批循环：每批 ≤500 条选 id 后物理删除，直到无待删行。
     * 双保险防死循环：批数上限 + 单批删除 0 行即退出（删除持续失败时不空转）。
     */
    private long batchLoop(Supplier<List<Long>> idsSelector, Function<List<Long>, Integer> deleter) {
        long deleted = 0;
        for (int i = 0; i < MAX_BATCHES_PER_QUERY; i++) {
            List<Long> ids = idsSelector.get();
            if (ids == null || ids.isEmpty()) {
                break;
            }
            int deletedRows = deleter.apply(ids);
            if (deletedRows == 0) {
                log.warn("Agent 数据清理: 本批 {} 条删除 0 行（可能被锁），退出该清理序列", ids.size());
                break;
            }
            deleted += deletedRows;
            if (ids.size() < AiDataCleanupMapper.BATCH_LIMIT) {
                break;
            }
        }
        return deleted;
    }

    /**
     * 读保留天数并换算截止时间；配置缺失用默认值。
     * 0 天=不保留（全部超期，用于测试/立即清理）；负数或非数字=非法，返回 null 跳过
     */
    private LocalDateTime daysCutoff(String configKey, int defaultDays) {
        int days = defaultDays;
        String value = configService.getConfigValueByKey(configKey);
        if (value != null && !value.isBlank()) {
            try {
                days = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (days < 0) {
            return null;
        }
        return LocalDateTime.now().minusDays(days);
    }
}
