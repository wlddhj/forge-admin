package com.forge.modules.workflow.service.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.CryptoUtils;
import com.forge.framework.mybatis.handler.EncryptTypeHandler;
import com.forge.framework.mybatis.holder.CryptoUtilsHolder;
import com.forge.modules.workflow.callback.CallbackDispatcher;
import com.forge.modules.workflow.dto.callback.CallbackRequest;
import com.forge.modules.workflow.dto.callback.CallbackResponse;
import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.mapper.WfBusinessCallbackMapper;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        var captor = ArgumentCaptor.forClass(WfBusinessCallback.class);
        verify(callbackMapper).insert(captor.capture());
        WfBusinessCallback saved = captor.getValue();
        assertEquals("taskCreated,instanceFinished", saved.getEvents());
        assertNull(saved.getProcessKey(), "空流程键归一 null=订阅全部");
        assertEquals(1, saved.getEnabled());
        assertEquals(5000, saved.getTimeoutMs());
        assertEquals("s3cret", saved.getSecret(), "明文原样交 TypeHandler 加密落库");
    }

    @Test
    @DisplayName("新增：secret 未填保持可空（不签名）")
    void create_secretOptional() {
        CallbackRequest req = request(List.of("taskCreated"));
        req.setSecret(null);
        service.createCallback(req);

        var captor = ArgumentCaptor.forClass(WfBusinessCallback.class);
        verify(callbackMapper).insert(captor.capture());
        assertNull(captor.getValue().getSecret());
    }

    @Test
    @DisplayName("出参：仅 hasSecret 标记，DTO 不再有 secret 明文字段")
    void page_hasSecretOnly() throws NoSuchMethodException {
        assertThrows(NoSuchMethodException.class, () -> CallbackResponse.class.getMethod("getSecret"),
                "CallbackResponse 不应保留 secret 出参");

        WfBusinessCallback configured = new WfBusinessCallback();
        configured.setSecret("s3cret");
        WfBusinessCallback blank = new WfBusinessCallback();
        blank.setSecret("");
        WfBusinessCallback absent = new WfBusinessCallback();
        when(callbackMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<WfBusinessCallback> page = inv.getArgument(0);
            page.setRecords(List.of(configured, blank, absent));
            page.setTotal(3);
            return page;
        });

        Page<CallbackResponse> result = service.pageCallback(1, 20, null, null);
        assertEquals(3, result.getRecords().size());
        assertTrue(result.getRecords().get(0).getHasSecret(), "有密钥 → true");
        assertFalse(result.getRecords().get(1).getHasSecret(), "空白串 → false");
        assertFalse(result.getRecords().get(2).getHasSecret(), "null → false");
    }

    @Test
    @DisplayName("更新：secret 留空保留原值（不置 null）")
    void update_blankSecretKeepsOriginal() {
        assertUpdatePreservesSecret("   ");
    }

    @Test
    @DisplayName("更新：secret 不传保留原值")
    void update_nullSecretKeepsOriginal() {
        assertUpdatePreservesSecret(null);
    }

    private void assertUpdatePreservesSecret(String submitted) {
        WfBusinessCallback existing = new WfBusinessCallback();
        existing.setId(7L);
        existing.setName("订单回调");
        existing.setSecret("orig-secret");
        existing.setEvents("taskCreated");
        when(callbackMapper.selectById(7L)).thenReturn(existing);

        CallbackRequest req = request(List.of("taskCreated"));
        req.setId(7L);
        req.setSecret(submitted);
        service.updateCallback(req);

        var captor = ArgumentCaptor.forClass(WfBusinessCallback.class);
        verify(callbackMapper).updateById(captor.capture());
        assertEquals("orig-secret", captor.getValue().getSecret(), "留空应保留原值");
    }

    @Test
    @DisplayName("防投毒：ENCv1: 前缀输入被剥离当明文重新加密，读回可解密为剥离后原文")
    void update_stripsEncPrefixAndReencrypts() throws Exception {
        // 与 WorkflowTestApplication 测试密钥一致，避免静态 Holder 跨用例串扰
        new CryptoUtilsHolder().setCryptoUtils(new CryptoUtils("test-aes-key-1234567890"));

        WfBusinessCallback existing = new WfBusinessCallback();
        existing.setId(7L);
        existing.setName("订单回调");
        existing.setSecret("orig-secret");
        existing.setEvents("taskCreated");
        when(callbackMapper.selectById(7L)).thenReturn(existing);

        CallbackRequest req = request(List.of("taskCreated"));
        req.setId(7L);
        req.setSecret("ENCv1:poisoned-body");
        service.updateCallback(req);

        var captor = ArgumentCaptor.forClass(WfBusinessCallback.class);
        verify(callbackMapper).updateById(captor.capture());
        WfBusinessCallback saved = captor.getValue();
        assertEquals("poisoned-body", saved.getSecret(), "ENCv1: 前缀应剥离当明文");

        // 写侧：TypeHandler 对剥离后的明文重新加密（库中为密文，非用户提交原样透传）
        EncryptTypeHandler handler = new EncryptTypeHandler();
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, saved.getSecret(), null);
        var psCaptor = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(eq(1), psCaptor.capture());
        String stored = psCaptor.getValue();
        assertTrue(stored.startsWith("ENCv1:"), "落库应为密文: " + stored);
        assertNotEquals("ENCv1:poisoned-body", stored, "不得原样透传投毒密文");

        // 读侧：再次读出可解密回剥离后明文
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("secret")).thenReturn(stored);
        assertEquals("poisoned-body", handler.getNullableResult(rs, "secret"));
    }

    @Test
    @DisplayName("实体接线：secret 挂 EncryptTypeHandler 且开启 autoResultMap（读侧才会解密）")
    void entity_secretWiredWithEncryptTypeHandler() throws NoSuchFieldException {
        TableField tableField = WfBusinessCallback.class.getDeclaredField("secret").getAnnotation(TableField.class);
        assertNotNull(tableField);
        assertEquals(EncryptTypeHandler.class, tableField.typeHandler());
        assertTrue(WfBusinessCallback.class.getAnnotation(TableName.class).autoResultMap(),
                "未开 autoResultMap 读侧不解密");
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
