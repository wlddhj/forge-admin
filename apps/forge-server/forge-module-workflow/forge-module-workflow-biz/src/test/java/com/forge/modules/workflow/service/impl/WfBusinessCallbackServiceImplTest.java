package com.forge.modules.workflow.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.workflow.callback.CallbackDispatcher;
import com.forge.modules.workflow.dto.callback.CallbackRequest;
import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.mapper.WfBusinessCallbackMapper;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 业务回调管理服务规则单测
 */
class WfBusinessCallbackServiceImplTest {

    private WfBusinessCallbackMapper callbackMapper;
    private WfCallbackRecordMapper recordMapper;
    private CallbackDispatcher dispatcher;
    private WfBusinessCallbackServiceImpl service;

    @BeforeEach
    void setUp() {
        callbackMapper = mock(WfBusinessCallbackMapper.class);
        recordMapper = mock(WfCallbackRecordMapper.class);
        dispatcher = mock(CallbackDispatcher.class);
        service = new WfBusinessCallbackServiceImpl(callbackMapper, recordMapper, dispatcher);
    }

    private CallbackRequest request(List<String> events) {
        CallbackRequest request = new CallbackRequest();
        request.setName("订单回调");
        request.setUrl("https://biz.example.com/hook");
        request.setSecret("s3cret");
        request.setEvents(events);
        request.setProcessKey("");
        return request;
    }

    @Test
    @DisplayName("新增：事件合法且空 processKey 归一为 null")
    void create_normalizesProcessKey() {
        service.createCallback(request(List.of("taskCreated", "instanceFinished")));

        var captor = org.mockito.ArgumentCaptor.forClass(WfBusinessCallback.class);
        verify(callbackMapper).insert(captor.capture());
        WfBusinessCallback saved = captor.getValue();
        assertEquals("taskCreated,instanceFinished", saved.getEvents());
        assertNull(saved.getProcessKey(), "空流程键归一 null=订阅全部");
        assertEquals(1, saved.getEnabled());
        assertEquals(5000, saved.getTimeoutMs());
    }

    @Test
    @DisplayName("新增：非法事件类型 400")
    void create_rejectsUnknownEvent() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.createCallback(request(List.of("magicEvent"))));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("新增：事件为空 400")
    void create_rejectsEmptyEvents() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.createCallback(request(List.of())));
        assertEquals(400, e.getCode());
    }

    @Test
    @DisplayName("重发：SUCCESS 记录拒绝")
    void resend_rejectsSuccessRecord() {
        WfCallbackRecord record = new WfCallbackRecord();
        record.setStatus("SUCCESS");
        when(recordMapper.selectById(1L)).thenReturn(record);

        BusinessException e = assertThrows(BusinessException.class, () -> service.resendRecord(1L));
        assertEquals(400, e.getCode());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("重发：回调配置禁用/缺失拒绝")
    void resend_rejectsDisabledCallback() {
        WfCallbackRecord record = new WfCallbackRecord();
        record.setStatus("FAILED");
        record.setCallbackId(9L);
        when(recordMapper.selectById(1L)).thenReturn(record);
        WfBusinessCallback disabled = new WfBusinessCallback();
        disabled.setEnabled(0);
        when(callbackMapper.selectById(9L)).thenReturn(disabled);

        assertThrows(BusinessException.class, () -> service.resendRecord(1L));
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("重发：FAILED + 启用配置 → 委托投递原 payload")
    void resend_dispatchesOriginalPayload() {
        WfCallbackRecord record = new WfCallbackRecord();
        record.setStatus("FAILED");
        record.setCallbackId(9L);
        record.setEventType("instanceFinished");
        record.setProcessKey("demo_leave");
        record.setBusinessKey("B-1");
        record.setInstanceId(77L);
        record.setPayload("{\"a\":1}");
        when(recordMapper.selectById(1L)).thenReturn(record);
        WfBusinessCallback enabled = new WfBusinessCallback();
        enabled.setId(9L);
        enabled.setEnabled(1);
        when(callbackMapper.selectById(9L)).thenReturn(enabled);

        service.resendRecord(1L);

        verify(dispatcher).deliver(enabled, "instanceFinished", "demo_leave", "B-1", 77L, null, null, "{\"a\":1}");
    }

    @Test
    @DisplayName("删除：不存在 404")
    void delete_missing404() {
        when(callbackMapper.selectById(5L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.deleteCallback(5L));
        assertEquals(404, e.getCode());
        verify(callbackMapper, never()).deleteById(any(Long.class));
    }
}
