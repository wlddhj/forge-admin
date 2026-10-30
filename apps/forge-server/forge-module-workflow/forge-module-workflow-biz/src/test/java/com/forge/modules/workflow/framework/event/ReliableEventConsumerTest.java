package com.forge.modules.workflow.framework.event;

import com.forge.modules.workflow.entity.WfEventAudit;
import com.forge.modules.workflow.mapper.WfEventAuditMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 事件可靠消费模板单测（重试覆写为同步直跑，退避数值由 BACKOFF 数组静态保证）
 */
class ReliableEventConsumerTest {

    private final WfEventAuditMapper auditMapper = mock(WfEventAuditMapper.class);

    /** 同步直跑重试的测试实例 */
    private final ReliableEventConsumer consumer = new ReliableEventConsumer(auditMapper, null) {
        @Override
        protected void scheduleRetry(Runnable retry, long delayMs) {
            retry.run();
        }
    };

    @Test
    @DisplayName("首试成功：不重试不落审计")
    void successFirstAttempt() {
        consumer.consume("L", 1L, () -> { });
        verify(auditMapper, never()).insert(any(WfEventAudit.class));
    }

    @Test
    @DisplayName("第二次成功：重试一次即止，不落审计")
    void successOnRetry() {
        int[] calls = {0};
        consumer.consume("L", 1L, () -> {
            if (++calls[0] == 1) {
                throw new IllegalStateException("first fail");
            }
        });
        assertEquals(2, calls[0]);
        verify(auditMapper, never()).insert(any(WfEventAudit.class));
    }

    @Test
    @DisplayName("四试全败：落 consumeFail 审计（监听器名+实例+异常摘要）")
    void finalFailureWritesAudit() {
        consumer.consume("WfAutoCopyEventHandler", 42L, () -> {
            throw new IllegalStateException("boom");
        });

        ArgumentCaptor<WfEventAudit> captor = ArgumentCaptor.forClass(WfEventAudit.class);
        verify(auditMapper, times(1)).insert(captor.capture());
        WfEventAudit audit = captor.getValue();
        assertEquals("consumeFail:WfAutoCopyEventHandler", audit.getEventType());
        assertEquals(42L, audit.getInstanceId());
        assertEquals("java.lang.IllegalStateException: boom", audit.getSummary());
    }

    @Test
    @DisplayName("审计写入自身失败：吞掉不抛（不能再引发调度线程异常）")
    void auditFailureSwallowed() {
        doThrow(new RuntimeException("db down")).when(auditMapper).insert(any(WfEventAudit.class));
        consumer.consume("L", null, () -> {
            throw new IllegalStateException("boom");
        });
        verify(auditMapper, times(1)).insert(any(WfEventAudit.class));
    }

    @Test
    @DisplayName("Error 同路径重试并终败审计")
    void errorAlsoRetried() {
        consumer.consume("L", 1L, () -> {
            throw new OutOfMemoryError("simulated");
        });
        ArgumentCaptor<WfEventAudit> captor = ArgumentCaptor.forClass(WfEventAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertEquals("java.lang.OutOfMemoryError: simulated", captor.getValue().getSummary());
        assertEquals(1L, captor.getValue().getInstanceId());
    }
}
