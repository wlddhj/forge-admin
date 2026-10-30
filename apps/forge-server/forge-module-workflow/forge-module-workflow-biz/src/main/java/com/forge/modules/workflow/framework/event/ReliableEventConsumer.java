package com.forge.modules.workflow.framework.event;

import com.forge.modules.workflow.entity.WfEventAudit;
import com.forge.modules.workflow.mapper.WfEventAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 进程内事件可靠消费模板：消费失败自动退避重试（2s/8s/30s，共 4 次尝试），
 * 终败落 wf_event_audit（eventType=consumeFail:监听器名），消费端需自身幂等。
 * <p>
 * 背景：平台事件消费者均为 @Async，异步线程内异常发布端不可见——失败即静默丢失。
 * 本模板补"重试 + 终败可见"，JVM 崩溃级不丢（发件箱）不在本期范围（见 06 文档）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ReliableEventConsumer {

    /**
     * 最大尝试次数：立即 1 次 + 退避重试 3 次
     */
    static final int MAX_ATTEMPTS = 4;

    private static final long[] BACKOFF_MILLIS = {2000, 8000, 30_000};

    private final WfEventAuditMapper auditMapper;
    private final ScheduledExecutorService scheduler;

    @Autowired
    public ReliableEventConsumer(WfEventAuditMapper auditMapper) {
        this(auditMapper, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "wf-event-retry");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /**
     * 测试注入专用：可控调度器
     */
    ReliableEventConsumer(WfEventAuditMapper auditMapper, ScheduledExecutorService scheduler) {
        this.auditMapper = auditMapper;
        this.scheduler = scheduler;
    }

    /**
     * 可靠执行一段事件消费逻辑：失败退避重试，终败写审计
     *
     * @param listenerName 监听器名（审计归属）
     * @param instanceId   关联实例ID（可空）
     * @param action       原消费逻辑（须幂等）
     */
    public void consume(String listenerName, Long instanceId, Runnable action) {
        attempt(listenerName, instanceId, action, 1);
    }

    private void attempt(String listenerName, Long instanceId, Runnable action, int round) {
        try {
            action.run();
        } catch (Throwable e) {
            if (round >= MAX_ATTEMPTS) {
                log.error("事件消费终败(共 {} 次): listener={}, instanceId={}", MAX_ATTEMPTS, listenerName, instanceId, e);
                saveConsumeFail(listenerName, instanceId, e);
                return;
            }
            long delayMs = BACKOFF_MILLIS[Math.min(round - 1, BACKOFF_MILLIS.length - 1)];
            log.warn("事件消费失败将重试({}/{}): listener={}, instanceId={}, {}ms 后重试, cause={}",
                    round, MAX_ATTEMPTS, listenerName, instanceId, delayMs, e.getMessage());
            final int nextRound = round + 1;
            scheduleRetry(() -> attempt(listenerName, instanceId, action, nextRound), delayMs);
        }
    }

    /**
     * 调度一次重试（测试可覆写为同步直跑）
     */
    protected void scheduleRetry(Runnable retry, long delayMs) {
        scheduler.schedule(retry, delayMs, TimeUnit.MILLISECONDS);
    }

    private void saveConsumeFail(String listenerName, Long instanceId, Throwable e) {
        try {
            WfEventAudit audit = new WfEventAudit();
            audit.setEventType("consumeFail:" + listenerName);
            audit.setInstanceId(instanceId);
            String message = e.getMessage() == null ? "" : e.getMessage();
            String summary = e.getClass().getName() + ": " + message;
            audit.setSummary(summary.length() > 900 ? summary.substring(0, 900) : summary);
            audit.setCreateTime(LocalDateTime.now());
            auditMapper.insert(audit);
        } catch (Exception auditError) {
            log.error("事件消费终败且审计写入失败: listener={}, instanceId={}", listenerName, instanceId, auditError);
        }
    }
}
