package com.forge.modules.workflow.callback;

import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 回调投递器池化+调度重试单测：
 * <ul>
 *   <li>timeoutMs 服务端钳制（60000→30000、空/非正→5000）</li>
 *   <li>失败重试经 scheduler.schedule(0/2/10s) 调度再提交，而非线程内 sleep</li>
 *   <li>投递池满（AbortPolicy 拒绝）视作一次失败进入既有重试链</li>
 *   <li>成功路径落 SUCCESS 记录且不调度重试</li>
 * </ul>
 * executor 用直通实现（提交即当前线程执行）驱动全链路，scheduler 用 mock 捕获
 * schedule 的 Runnable 后手动 run 推进，测试本身零等待。
 */
@ExtendWith(MockitoExtension.class)
class CallbackDispatcherTest {

    @Mock
    private WfCallbackRecordMapper recordMapper;

    @Mock
    private ScheduledExecutorService scheduler;

    /** 直通执行器：提交即在测试线程同步执行（模拟投递池立即受理） */
    private final Executor directExecutor = Runnable::run;

    /** 恒拒绝执行器：模拟 callbackExecutor 池+队列满触发 AbortPolicy */
    private final Executor rejectingExecutor = task -> {
        throw new RejectedExecutionException("pool full");
    };

    private HttpServer okServer;
    private int okPort;

    @BeforeEach
    void startOkServer() throws IOException {
        okServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        okServer.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("ok".getBytes(StandardCharsets.UTF_8));
            }
        });
        okServer.start();
        okPort = okServer.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (okServer != null) {
            okServer.stop(0);
        }
    }

    private CallbackDispatcher dispatcher(Executor executor) {
        return new CallbackDispatcher(recordMapper, executor, scheduler);
    }

    private WfBusinessCallback callback(String url, Integer timeoutMs) {
        WfBusinessCallback cb = new WfBusinessCallback();
        cb.setId(1L);
        cb.setName("单测回调");
        cb.setUrl(url);
        cb.setTimeoutMs(timeoutMs);
        return cb;
    }

    @Test
    @DisplayName("timeoutMs 服务端钳制：空/非正取默认 5000，60000 压到 30000")
    void resolveTimeoutMs_clampsToCap() {
        assertEquals(5000, CallbackDispatcher.resolveTimeoutMs(null));
        assertEquals(5000, CallbackDispatcher.resolveTimeoutMs(0));
        assertEquals(5000, CallbackDispatcher.resolveTimeoutMs(-1));
        assertEquals(3000, CallbackDispatcher.resolveTimeoutMs(3000));
        assertEquals(30000, CallbackDispatcher.resolveTimeoutMs(30000));
        assertEquals(30000, CallbackDispatcher.resolveTimeoutMs(60000));
    }

    @Test
    @DisplayName("失败重试走 scheduler.schedule：0 失败→2s，再失败→10s，耗尽落 FAILED(retryCount=2)")
    void deliver_failuresScheduledNotSlept() {
        // 端口 1 上无服务：连接立即被拒，快速失败
        CallbackDispatcher dispatcher = dispatcher(directExecutor);

        dispatcher.deliver(callback("http://127.0.0.1:1/hook", 500),
                "instanceFinished", "pk", "bk", 1L, null, null, "{}");

        // 首试失败：不 sleep，调度 2s 后再提交
        ArgumentCaptor<Runnable> firstRetry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(firstRetry.capture(), eq(2000L), eq(TimeUnit.MILLISECONDS));
        // 手动触发第一次重试 → 再失败 → 调度 10s
        firstRetry.getValue().run();
        ArgumentCaptor<Runnable> secondRetry = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(secondRetry.capture(), eq(10000L), eq(TimeUnit.MILLISECONDS));
        // 手动触发第二次重试 → 再失败 → 重试耗尽，落 FAILED
        secondRetry.getValue().run();

        ArgumentCaptor<WfCallbackRecord> recordCaptor = ArgumentCaptor.forClass(WfCallbackRecord.class);
        verify(recordMapper, times(1)).insert(recordCaptor.capture());
        WfCallbackRecord record = recordCaptor.getValue();
        assertEquals("FAILED", record.getStatus());
        assertEquals(2, record.getRetryCount(), "首次+2次重试，retryCount 记到 2");
    }

    @Test
    @DisplayName("投递池满（AbortPolicy 拒绝）：视作一次失败进入重试链，未耗尽不落 FAILED")
    void deliver_poolRejectionTreatedAsFailure() {
        CallbackDispatcher dispatcher = dispatcher(rejectingExecutor);

        dispatcher.deliver(callback("http://127.0.0.1:" + okPort + "/hook", 500),
                "instanceFinished", "pk", "bk", 1L, null, null, "{}");

        // 提交被拒按失败计：调度 2s 后重试，而非直接丢弃或立刻落 FAILED
        verify(scheduler).schedule(any(Runnable.class), eq(2000L), eq(TimeUnit.MILLISECONDS));
        verify(recordMapper, never()).insert(any(WfCallbackRecord.class));
    }

    @Test
    @DisplayName("成功投递：落 SUCCESS 记录，不调度任何重试")
    void deliver_successSavesRecordWithoutRetry() {
        CallbackDispatcher dispatcher = dispatcher(directExecutor);

        dispatcher.deliver(callback("http://127.0.0.1:" + okPort + "/hook", 2000),
                "instanceFinished", "pk", "bk", 1L, null, null, "{}");

        ArgumentCaptor<WfCallbackRecord> recordCaptor = ArgumentCaptor.forClass(WfCallbackRecord.class);
        verify(recordMapper, times(1)).insert(recordCaptor.capture());
        WfCallbackRecord record = recordCaptor.getValue();
        assertEquals("SUCCESS", record.getStatus());
        assertEquals(200, record.getHttpStatus());
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }
}
