package com.forge.modules.workflow.callback;

import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.InstanceEventType;
import com.aizuda.bpm.engine.core.enums.TaskEventType;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.entity.WfEventAudit;
import com.forge.modules.workflow.framework.event.ReliableEventConsumer;
import com.forge.modules.workflow.listener.event.WorkflowInstanceEvent;
import com.forge.modules.workflow.listener.event.WorkflowTaskEvent;
import com.forge.modules.workflow.mapper.WfBusinessCallbackMapper;
import com.forge.modules.workflow.mapper.WfEventAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 回调监听器租户隔离单测：
 * 事件携带租户优先（压过上下文）、上下文回退、无租户来源 fail-closed 不投递、实体字段护栏。
 * <p>
 * 事件用 Mockito 桩（getTenantId 由任务 #3 落地），租户断言锚定
 * "enabledCallbacks 查询发生时刻的 TenantContextHolder 值"——即租户拦截器实际生效的口径。
 */
class WfBusinessCallbackEventListenerTest {

    private WfBusinessCallbackMapper callbackMapper;
    private CallbackPayloadBuilder payloadBuilder;
    private CallbackDispatcher dispatcher;
    private WfEventAuditMapper auditMapper;
    private BusinessCallbackEventListener listener;

    @BeforeEach
    void setUp() {
        callbackMapper = mock(WfBusinessCallbackMapper.class);
        payloadBuilder = mock(CallbackPayloadBuilder.class);
        dispatcher = mock(CallbackDispatcher.class);
        auditMapper = mock(WfEventAuditMapper.class);
        // 真实 ReliableEventConsumer：consume 同步执行 action，终败才写审计（mock 审计 mapper）
        listener = new BusinessCallbackEventListener(callbackMapper, payloadBuilder, dispatcher,
                mock(ProcessService.class), mock(QueryService.class), new ReliableEventConsumer(auditMapper),
                new com.forge.framework.tenant.config.TenantProperties());
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private WfBusinessCallback enabledCallback() {
        WfBusinessCallback callback = new WfBusinessCallback();
        callback.setId(9L);
        callback.setName("租户2回调");
        callback.setEvents("taskCompleted,instanceFinished");
        callback.setEnabled(1);
        callback.setIncludeVariables(0);
        return callback;
    }

    @Test
    @DisplayName("租户2任务事件：查询构造于租户2上下文内，投递 enabled 回调，结束恢复原上下文")
    void taskEvent_eventTenantPriority() {
        WorkflowTaskEvent event = mock(WorkflowTaskEvent.class);
        when(event.getTenantId()).thenReturn(2L);
        when(event.getEventType()).thenReturn(TaskEventType.complete);
        when(event.getInstanceId()).thenReturn(77L);
        when(event.getTask()).thenReturn(null);

        AtomicLong tenantAtQuery = new AtomicLong(-1L);
        when(callbackMapper.selectList(any())).thenAnswer(inv -> {
            tenantAtQuery.set(TenantContextHolder.getTenantId());
            return List.of(enabledCallback());
        });
        when(payloadBuilder.build(eq(CallbackEventType.taskCompleted), same(event), isNull()))
                .thenReturn("{\"a\":1}");
        // 上下文置 1：事件携带值（2）必须压过上下文（1）
        TenantContextHolder.setTenantId(1L);

        listener.onTaskEvent(event);

        assertEquals(2L, tenantAtQuery.get(), "enabledCallbacks 查询应发生在租户2上下文内");
        assertEquals(1L, TenantContextHolder.getTenantId(), "处理段结束应恢复原上下文");

        ArgumentCaptor<WfBusinessCallback> captor = ArgumentCaptor.forClass(WfBusinessCallback.class);
        verify(dispatcher, times(1)).deliver(captor.capture(), eq("taskCompleted"),
                isNull(), isNull(), isNull(), isNull(), isNull(), eq("{\"a\":1}"));
        assertEquals(9L, captor.getValue().getId());
        verify(auditMapper, never()).insert(any(WfEventAudit.class));
    }

    @Test
    @DisplayName("事件缺租户回退上下文：租户2上下文下实例终态事件正常投递，结束后上下文清空")
    void instanceEvent_fallbackToContext() {
        WorkflowInstanceEvent event = mock(WorkflowInstanceEvent.class);
        when(event.getTenantId()).thenReturn(null);
        when(event.getEventType()).thenReturn(InstanceEventType.end);
        when(event.getInstanceId()).thenReturn(88L);
        when(event.getProcessId()).thenReturn(null);
        when(event.getBusinessKey()).thenReturn("B-1");

        AtomicLong tenantAtQuery = new AtomicLong(-1L);
        when(callbackMapper.selectList(any())).thenAnswer(inv -> {
            tenantAtQuery.set(TenantContextHolder.getTenantId());
            return List.of(enabledCallback());
        });
        when(payloadBuilder.build(same(event), isNull(), isNull(), eq(false)))
                .thenReturn("{\"fin\":true}");
        TenantContextHolder.setTenantId(2L);

        listener.onInstanceEvent(event);

        assertEquals(2L, tenantAtQuery.get(), "回退路径下查询仍应在租户2上下文内");
        assertEquals(2L, TenantContextHolder.getTenantId(), "处理段结束应恢复原上下文(2)");
        verify(dispatcher, times(1)).deliver(any(WfBusinessCallback.class), eq("instanceFinished"),
                isNull(), eq("B-1"), eq(88L), isNull(), isNull(), eq("{\"fin\":true}"));
    }

    @Test
    @DisplayName("事件携带租户且原上下文为空：投递后上下文清空不残留（池线程防污染）")
    void taskEvent_clearsContextWhenNoPrevious() {
        WorkflowTaskEvent event = mock(WorkflowTaskEvent.class);
        when(event.getTenantId()).thenReturn(2L);
        when(event.getEventType()).thenReturn(TaskEventType.complete);
        when(event.getInstanceId()).thenReturn(77L);
        when(event.getTask()).thenReturn(null);

        when(callbackMapper.selectList(any())).thenReturn(List.of(enabledCallback()));
        when(payloadBuilder.build(eq(CallbackEventType.taskCompleted), same(event), isNull()))
                .thenReturn("{\"a\":1}");

        listener.onTaskEvent(event);

        assertNull(TenantContextHolder.getTenantId(), "原上下文为 null 时处理段结束应清空");
        verify(dispatcher, times(1)).deliver(any(WfBusinessCallback.class), eq("taskCompleted"),
                any(), any(), any(), any(), any(), eq("{\"a\":1}"));
    }

    @Test
    @DisplayName("无租户来源（事件 null 且上下文 null）：任务事件告警跳过，零投递零审计")
    void taskEvent_failClosedWithoutTenant() {
        WorkflowTaskEvent event = mock(WorkflowTaskEvent.class);
        when(event.getTenantId()).thenReturn(null);
        when(event.getEventType()).thenReturn(TaskEventType.complete);
        when(event.getInstanceId()).thenReturn(77L);

        listener.onTaskEvent(event);

        verifyNoInteractions(callbackMapper, payloadBuilder, dispatcher, auditMapper);
    }

    @Test
    @DisplayName("无租户来源：实例终态事件同样告警跳过，零投递零审计")
    void instanceEvent_failClosedWithoutTenant() {
        WorkflowInstanceEvent event = mock(WorkflowInstanceEvent.class);
        when(event.getTenantId()).thenReturn(null);
        when(event.getEventType()).thenReturn(InstanceEventType.end);
        when(event.getInstanceId()).thenReturn(88L);

        listener.onInstanceEvent(event);

        verifyNoInteractions(callbackMapper, payloadBuilder, dispatcher, auditMapper);
    }

    @Test
    @DisplayName("实体护栏：三实体均含 Long tenantId 字段（租户列映射依赖）")
    void entities_haveTenantIdField() throws NoSuchFieldException {
        for (Class<?> entity : List.of(WfBusinessCallback.class, WfCallbackRecord.class, WfEventAudit.class)) {
            assertEquals(Long.class, entity.getDeclaredField("tenantId").getType(),
                    entity.getSimpleName() + " 缺 tenantId 字段");
        }
    }
}
