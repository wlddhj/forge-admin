package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.workflow.callback.CallbackSigner;
import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.mapper.WfBusinessCallbackMapper;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 端到端：业务回调投递链路
 * 内嵌 HttpServer 模拟业务方：签名投递成功 / 失败重试落 FAILED / 过滤矩阵
 */
class E2eCallbackDeliveryTest extends WorkflowIntegrationTestBase {

    @Autowired
    private WfBusinessCallbackMapper callbackMapper;

    @Autowired
    private WfCallbackRecordMapper recordMapper;

    private HttpServer server;
    private int port;
    private final ConcurrentLinkedQueue<String> receivedBodies = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String[]> receivedHeaders = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void startServer() throws IOException {
        receivedBodies.clear();
        receivedHeaders.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 接收端：记录 body 与签名头，返回 200 */
    private void serveOk() {
        server.createContext("/hook", exchange -> {
            try (InputStream in = exchange.getRequestBody();
                 var out = exchange.getResponseBody()) {
                receivedBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                receivedHeaders.add(new String[]{
                        exchange.getRequestHeaders().getFirst("X-Forge-Timestamp"),
                        exchange.getRequestHeaders().getFirst("X-Forge-Signature")});
                exchange.sendResponseHeaders(200, 2);
                out.write("ok".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    /** 接收端：恒 500（触发重试耗尽） */
    private void serve500() {
        server.createContext("/hook", exchange -> {
            try (var out = exchange.getResponseBody()) {
                exchange.sendResponseHeaders(500, 2);
                out.write("no".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    private Long registerCallback(String events, String processKey, String secret, int enabled) {
        WfBusinessCallback cb = new WfBusinessCallback();
        cb.setName("E2E回调-" + UUID.randomUUID().toString().substring(0, 6));
        cb.setUrl("http://127.0.0.1:" + port + "/hook");
        cb.setSecret(secret);
        cb.setEvents(events);
        cb.setProcessKey(processKey);
        cb.setIncludeVariables(0);
        cb.setEnabled(enabled);
        cb.setTimeoutMs(2000);
        callbackMapper.insert(cb);
        return cb.getId();
    }

    /** 发起并办结一条串行流程，返回实例 id */
    private Long runSimpleProcess(String processKey) {
        String model = """
                {"name":"回调测试流程","key":"%s",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """.formatted(processKey);
        Long processId = deploy(model);
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (Map<String, Object>) null).orElseThrow();
        Long instanceId = instance.getId();
        List<FlwTask> tasks = activeTasks(instanceId);
        assertEquals(1, tasks.size());
        flowLongEngine.executeTask(tasks.get(0).getId(), user(2L, "审批人乙"), null);
        assertEquals(InstanceState.complete.getValue(),
                queryService.getHistInstance(instanceId).getInstanceState());
        return instanceId;
    }

    private List<WfCallbackRecord> awaitRecords(Long callbackId, int expected) {
        awaitCond(25000, () -> recordMapper.selectCount(new LambdaQueryWrapper<WfCallbackRecord>()
                .eq(WfCallbackRecord::getCallbackId, callbackId)).intValue() >= expected);
        return recordMapper.selectList(new LambdaQueryWrapper<WfCallbackRecord>()
                .eq(WfCallbackRecord::getCallbackId, callbackId));
    }

    /** 轮询等待条件成立（替代 awaitility） */
    private static void awaitCond(long timeoutMillis, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("等待条件超时(" + timeoutMillis + "ms)");
    }

    @Test
    @DisplayName("成功投递：三事件全订阅，接收端签名可验证，三条记录 SUCCESS")
    void delivery_successWithVerifiableSignature() {
        serveOk();
        String secret = "e2e-secret";
        Long cbId = registerCallback("taskCreated,taskCompleted,instanceFinished", null, secret, 1);

        Long instanceId = runSimpleProcess("e2e_cb_ok_" + UUID.randomUUID().toString().substring(0, 4));

        List<WfCallbackRecord> records = awaitRecords(cbId, 3);
        assertEquals(3, records.size());
        assertTrue(records.stream().allMatch(r -> "SUCCESS".equals(r.getStatus())),
                "全部应 SUCCESS: " + records);

        awaitCond(10000, () -> receivedBodies.size() >= 3);
        assertEquals(3, receivedBodies.size(), "接收端应收到 3 次投递");
        var bodies = List.copyOf(receivedBodies);
        var headers = List.copyOf(receivedHeaders);
        for (int i = 0; i < bodies.size(); i++) {
            String body = bodies.get(i);
            String[] header = headers.get(i);
            assertNotNull(header[0], "缺时间戳头");
            assertTrue(CallbackSigner.verify(secret, header[0], body, header[1]),
                    "签名验证失败: " + body);
            // 全局 Jackson 将 Long 序列化为字符串，断言按带引号形态匹配
            assertTrue(body.contains("\"instanceId\":\"" + instanceId + "\""), "payload 缺实例ID: " + body);
        }
        assertTrue(bodies.stream().anyMatch(b -> b.contains("\"eventType\":\"taskCreated\"")));
        assertTrue(bodies.stream().anyMatch(b -> b.contains("\"eventType\":\"taskCompleted\"")));
        assertTrue(bodies.stream().anyMatch(b -> b.contains("\"eventType\":\"instanceFinished\"")));
    }

    @Test
    @DisplayName("失败重试：对端恒 500 → 3 次尝试后落 FAILED 且带 http_status")
    void delivery_failureRetriesThenFailed() {
        serve500();
        Long cbId = registerCallback("instanceFinished", null, null, 1);

        runSimpleProcess("e2e_cb_fail_" + UUID.randomUUID().toString().substring(0, 4));

        List<WfCallbackRecord> records = awaitRecords(cbId, 1);
        assertEquals(1, records.size());
        WfCallbackRecord record = records.get(0);
        assertEquals("FAILED", record.getStatus());
        assertEquals(500, record.getHttpStatus());
        assertNotNull(record.getErrorMsg());
        assertEquals(2, record.getRetryCount(), "首次+2次重试，retryCount 记到 2");
    }

    @Test
    @DisplayName("过滤矩阵：process_key 不匹配不投、禁用不投（零记录零接收）")
    void delivery_filtered() {
        serveOk();
        // 键不匹配目标：只订 e2e_cb_target，但跑 e2e_cb_other
        Long wrongKeyCb = registerCallback("instanceFinished", "e2e_cb_target", null, 1);
        // 禁用回调：全订阅但 enabled=0
        Long disabledCb = registerCallback("taskCreated,taskCompleted,instanceFinished", null, null, 0);

        runSimpleProcess("e2e_cb_other_" + UUID.randomUUID().toString().substring(0, 4));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }
        assertTrue(receivedBodies.isEmpty(), "键不匹配/禁用均不应投递");
        assertEquals(0, recordMapper.selectCount(new LambdaQueryWrapper<WfCallbackRecord>()
                .in(WfCallbackRecord::getCallbackId, List.of(wrongKeyCb, disabledCb))).intValue());
    }
}
