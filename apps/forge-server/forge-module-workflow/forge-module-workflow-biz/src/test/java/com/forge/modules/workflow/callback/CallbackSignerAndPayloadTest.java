package com.forge.modules.workflow.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回调签名与负载纯函数单元测试
 */
class CallbackSignerAndPayloadTest {

    private final CallbackPayloadBuilder builder = new CallbackPayloadBuilder(new ObjectMapper());

    @Test
    @DisplayName("签名可复算：HMAC-SHA256(secret, timestamp.body)")
    void sign_deterministicAndVerifiable() {
        String sig1 = CallbackSigner.sign("secret123", "1700000000000", "{\"a\":1}");
        String sig2 = CallbackSigner.sign("secret123", "1700000000000", "{\"a\":1}");

        assertNotNull(sig1);
        assertEquals(sig1, sig2, "同输入签名确定");
        assertNotEquals(sig1, CallbackSigner.sign("secret123", "1700000000001", "{\"a\":1}"), "时间戳参与签名");
        assertNotEquals(sig1, CallbackSigner.sign("other", "1700000000000", "{\"a\":1}"), "密钥参与签名");
        assertTrue(CallbackSigner.verify("secret123", "1700000000000", "{\"a\":1}", sig1));
        assertFalse(CallbackSigner.verify("secret123", "1700000000000", "{\"a\":2}", sig1), "篡改 body 验签失败");
        assertFalse(CallbackSigner.verify("wrong", "1700000000000", "{\"a\":1}", sig1), "错误密钥验签失败");
    }

    @Test
    @DisplayName("空密钥不签名（返回 null），验签拒绝")
    void sign_blankSecretSkips() {
        assertNull(CallbackSigner.sign("", "1", "b"));
        assertNull(CallbackSigner.sign(null, "1", "b"));
        assertFalse(CallbackSigner.verify("", "1", "b", "anything"));
    }

    @Test
    @DisplayName("任务事件负载契约字段齐全")
    void payload_taskEventContract() {
        com.forge.modules.workflow.listener.event.WorkflowTaskEvent event =
                new com.forge.modules.workflow.listener.event.WorkflowTaskEvent(this,
                        com.aizuda.bpm.engine.core.enums.TaskEventType.create,
                        taskOf(88L, 999L, "主管审批", "ap_mgr"),
                        java.util.List.of(), null,
                        com.aizuda.bpm.engine.core.FlowCreator.of("1", "张三"));

        String json = builder.build(CallbackEventType.taskCreated, event, "demo_leave");
        Map<String, Object> payload = read(json);

        assertEquals("taskCreated", payload.get("eventType"));
        assertEquals("demo_leave", payload.get("processKey"));
        assertEquals(999L, ((Number) payload.get("instanceId")).longValue());
        assertEquals(88L, ((Number) payload.get("taskId")).longValue());
        assertEquals("主管审批", payload.get("taskName"));
        assertEquals("ap_mgr", payload.get("nodeKey"));
        assertEquals("张三", payload.get("operatorName"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    @DisplayName("实例终态负载：instanceState 默认不带 variables，开启后携带")
    void payload_instanceEventVariables() {
        com.aizuda.bpm.engine.entity.FlwHisInstance instance = new com.aizuda.bpm.engine.entity.FlwHisInstance();
        instance.setId(555L);
        instance.setBusinessKey("BIZ-1");
        instance.setInstanceState(1);
        instance.setVariable("{\"amount\": 8000}");
        com.forge.modules.workflow.listener.event.WorkflowInstanceEvent event =
                new com.forge.modules.workflow.listener.event.WorkflowInstanceEvent(this,
                        com.aizuda.bpm.engine.core.enums.InstanceEventType.end, instance, null,
                        com.aizuda.bpm.engine.core.FlowCreator.of("1", "张三"));

        Map<String, Object> withoutVars = read(builder.build(event, "demo_ai_route", "AI智能分发", false));
        assertEquals("instanceFinished", withoutVars.get("eventType"));
        assertEquals("BIZ-1", withoutVars.get("businessKey"));
        assertEquals(1, ((Number) withoutVars.get("instanceState")).intValue());
        assertFalse(withoutVars.containsKey("variables"), "默认不带变量");

        Map<String, Object> withVars = read(builder.build(event, "demo_ai_route", "AI智能分发", true));
        assertTrue(withVars.containsKey("variables"));
        assertEquals(8000, ((Number) ((Map<?, ?>) withVars.get("variables")).get("amount")).intValue());
    }

    private com.aizuda.bpm.engine.entity.FlwTask taskOf(Long id, Long instanceId, String name, String taskKey) {
        com.aizuda.bpm.engine.entity.FlwTask task = new com.aizuda.bpm.engine.entity.FlwTask();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setTaskName(name);
        task.setTaskKey(taskKey);
        return task;
    }

    private Map<String, Object> read(String json) {
        try {
            return new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
