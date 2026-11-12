package com.forge.modules.workflow.test;

import com.aizuda.bpm.engine.core.enums.InstanceState;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.entity.WfEventAudit;
import com.forge.modules.workflow.mapper.WfBusinessCallbackMapper;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import com.forge.modules.workflow.mapper.WfEventAuditMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开租户 E2E 冒烟（租户开闸前置 T10）：
 * TENANT_ENABLE=true 下 发起→审批→业务回调投递→事件审计写入 全链不 NPE，
 * 且回调审计/事件审计数据落正确租户（TENANT 上下文=1）。
 */
class TenantOnE2eSmokeTest extends TenantEnabledTestBase {

    @Autowired
    private WfBusinessCallbackMapper callbackMapper;
    @Autowired
    private WfCallbackRecordMapper recordMapper;
    @Autowired
    private WfEventAuditMapper eventAuditMapper;

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> receivedBodies = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void startServer() throws Exception {
        receivedBodies.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            try (InputStream in = exchange.getRequestBody();
                 var out = exchange.getResponseBody()) {
                receivedBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(200, 2);
                out.write("ok".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("开租户下全链冒烟：回调投递成功、审计落库租户正确")
    void fullChainShouldWorkWithTenantEnabled() {
        // 注册终态回调（租户上下文=1，由 Base @BeforeEach setTenantId(1) 提供）
        WfBusinessCallback cb = new WfBusinessCallback();
        cb.setName("开租户冒烟-" + UUID.randomUUID().toString().substring(0, 6));
        cb.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        cb.setSecret("smoke-secret");
        cb.setEvents("taskCompleted,instanceFinished");
        cb.setProcessKey("");
        cb.setIncludeVariables(0);
        cb.setEnabled(1);
        cb.setTimeoutMs(2000);
        callbackMapper.insert(cb);

        // 发起并办结（租户 1 上下文，引擎各表 SQL 由租户插件自动注入 tenant_id）
        String model = """
                {"name":"开租户冒烟流程","key":"tenant_smoke",
                 "nodeConfig":{"nodeName":"发起人","nodeKey":"start","type":0,
                   "childNode":{"nodeName":"审批","nodeKey":"ap1","type":1,"setType":1,"examineMode":3,
                     "nodeAssigneeList":[{"id":"2","name":"审批人乙"}]}}}
                """;
        Long processId = deploy(model);
        var instance = flowLongEngine.startInstanceById(processId, user(1L, "发起人甲"),
                (Map<String, Object>) null).orElseThrow();
        Long instanceId = instance.getId();
        List<FlwTask> tasks = activeTasks(instanceId);
        assertEquals(1, tasks.size());
        flowLongEngine.executeTask(tasks.get(0).getId(), user(2L, "审批人乙"), null);
        assertEquals(InstanceState.complete.getValue(),
                queryService.getHistInstance(instanceId).getInstanceState());

        // AFTER_COMMIT 异步投递：等待接收端收到回调
        waitFor("回调送达", () -> !receivedBodies.isEmpty());
        assertTrue(receivedBodies.peek().contains("taskCompleted") || receivedBodies.peek().contains("instanceFinished")
                        || receivedBodies.peek().contains(instanceId.toString()),
                "回调 body 应携带事件/实例信息: " + receivedBodies.peek());

        // 投递审计与事件审计均落租户 1
        waitFor("投递审计落库", () -> recordMapper.selectCount(new LambdaQueryWrapper<WfCallbackRecord>()
                .eq(WfCallbackRecord::getInstanceId, instanceId)) > 0);
        WfCallbackRecord record = recordMapper.selectList(new LambdaQueryWrapper<WfCallbackRecord>()
                .eq(WfCallbackRecord::getInstanceId, instanceId)).get(0);
        assertEquals(1L, record.getTenantId(), "投递审计应落当前租户");
        assertTrue(record.getResponseBody() != null || record.getStatus() != null, "审计应有投递结果");

        waitFor("事件审计落库", () -> eventAuditMapper.selectCount(new LambdaQueryWrapper<WfEventAudit>()
                .eq(WfEventAudit::getInstanceId, instanceId)) > 0);
    }

    /** 轮询等待条件成立（10 秒上限，同 E2eCallbackDeliveryTest 模式） */
    private void waitFor(String what, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("等待超时: " + what);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待被中断: " + what);
            }
        }
    }
}
