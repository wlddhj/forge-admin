package com.forge.modules.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.FluxSink;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 流取消闸单元测试（稳定性批次 T1）：
 * 下游取消后帧投递点立即抛出终止同步读循环，socket/线程即时释放。
 */
class StreamCancelGuardTest {

    @SuppressWarnings("unchecked")
    private final FluxSink<StreamFrame> emitter = Mockito.mock(FluxSink.class);
    private final OpenAiCompatStreamClient.CancelGuard guard = new OpenAiCompatStreamClient.CancelGuard(emitter);

    @Test
    @DisplayName("未取消时帧正常透传")
    void frameShouldPassThroughWhenNotCancelled() {
        guard.accept(StreamFrame.content("hi"));
        verify(emitter).next(Mockito.<StreamFrame>any());
    }

    @Test
    @DisplayName("取消后首个帧投递点抛 UpstreamStreamException，读循环随之终止")
    void cancelledGuardShouldThrowOnNextFrame() {
        guard.cancel();
        assertTrue(guard.isCancelled());
        assertThrows(OpenAiCompatStreamClient.UpstreamStreamException.class,
                () -> guard.accept(StreamFrame.content("hi")));
        verify(emitter, never()).next(Mockito.<StreamFrame>any());
    }

    @Test
    @DisplayName("onCancel 与 onDispose 双通道都置位（幂等）")
    void cancelShouldBeIdempotent() {
        guard.cancel();
        guard.cancel();
        assertTrue(guard.isCancelled());
    }
}
