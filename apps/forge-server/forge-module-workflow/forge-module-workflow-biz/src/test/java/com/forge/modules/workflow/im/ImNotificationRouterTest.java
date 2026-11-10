package com.forge.modules.workflow.im;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路由器单测：渠道过滤、未绑定跳过、部分失败不抛、全部失败才抛
 */
class ImNotificationRouterTest {

    private ImNotificationChannel wework;
    private ImNotificationChannel dingtalk;
    private ImUserBindingResolver resolver;
    private ImNotificationRouter router;

    private final ImNotificationMessage message = new ImNotificationMessage(
            "taskCreated", "t", "c", "http://x", 1L, 2L, "pk");

    @BeforeEach
    void setUp() {
        wework = Mockito.mock(ImNotificationChannel.class);
        dingtalk = Mockito.mock(ImNotificationChannel.class);
        when(wework.channel()).thenReturn("wework");
        when(dingtalk.channel()).thenReturn("dingtalk");
        resolver = Mockito.mock(ImUserBindingResolver.class);
        router = new ImNotificationRouter(List.of(wework, dingtalk), resolver);
    }

    @Test
    void dispatch_DisabledChannelsSkipped() {
        when(wework.enabled()).thenReturn(true);
        when(dingtalk.enabled()).thenReturn(false);
        when(resolver.resolve(eq("wework"), anyCollection())).thenReturn(Map.of(1L, "ww-1"));
        when(wework.sendToUser(eq("ww-1"), any())).thenReturn(ImSendResult.ok());

        router.dispatch(message, List.of(1L, 2L));

        verify(wework).sendToUser("ww-1", message);
        verify(dingtalk, never()).sendToUser(any(), any());
    }

    @Test
    void dispatch_NoEnabledChannels_NoOp() {
        when(wework.enabled()).thenReturn(false);
        when(dingtalk.enabled()).thenReturn(false);

        assertDoesNotThrow(() -> router.dispatch(message, List.of(1L)));
        verify(resolver, never()).resolve(any(), anyCollection());
    }

    @Test
    void dispatch_EmptyUsers_NoOp() {
        when(wework.enabled()).thenReturn(true);
        assertDoesNotThrow(() -> router.dispatch(message, List.of()));
        verify(resolver, never()).resolve(any(), anyCollection());
    }

    @Test
    void dispatch_UnboundUsersSkippedWithoutSend() {
        when(wework.enabled()).thenReturn(true);
        when(dingtalk.enabled()).thenReturn(false);
        when(resolver.resolve(eq("wework"), anyCollection())).thenReturn(Map.of());

        assertDoesNotThrow(() -> router.dispatch(message, List.of(1L, 2L)));
        verify(wework, never()).sendToUser(any(), any());
    }

    @Test
    void dispatch_PartialChannelFailure_DoesNotThrow() {
        when(wework.enabled()).thenReturn(true);
        when(dingtalk.enabled()).thenReturn(true);
        when(resolver.resolve(eq("wework"), anyCollection())).thenReturn(Map.of(1L, "ww-1"));
        when(resolver.resolve(eq("dingtalk"), anyCollection())).thenReturn(Map.of(1L, "dd-1"));
        when(wework.sendToUser(eq("ww-1"), any())).thenReturn(ImSendResult.ok());
        when(dingtalk.sendToUser(eq("dd-1"), any())).thenReturn(ImSendResult.fail("boom"));

        assertDoesNotThrow(() -> router.dispatch(message, List.of(1L)));
    }

    @Test
    void dispatch_AllChannelsFail_ThrowsForRetry() {
        when(wework.enabled()).thenReturn(true);
        when(dingtalk.enabled()).thenReturn(true);
        when(resolver.resolve(eq("wework"), anyCollection())).thenReturn(Map.of(1L, "ww-1"));
        when(resolver.resolve(eq("dingtalk"), anyCollection())).thenReturn(Map.of(1L, "dd-1"));
        when(wework.sendToUser(eq("ww-1"), any())).thenReturn(ImSendResult.fail("ww down"));
        when(dingtalk.sendToUser(eq("dd-1"), any())).thenReturn(ImSendResult.fail("dd down"));

        assertThrows(IllegalStateException.class, () -> router.dispatch(message, List.of(1L)));
    }

    @Test
    void dispatch_AllUnbound_DoesNotThrow() {
        when(wework.enabled()).thenReturn(true);
        when(dingtalk.enabled()).thenReturn(true);
        when(resolver.resolve(any(), anyCollection())).thenReturn(Map.of());

        assertDoesNotThrow(() -> router.dispatch(message, List.of(1L)));
    }
}
