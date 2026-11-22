package com.forge.modules.workflow.service.bizview;

import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.forge.common.exception.BusinessException;
import com.forge.modules.system.dto.attachment.AttachmentContent;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.workflow.dto.bizview.WfBizViewAttachmentRef;
import com.forge.modules.workflow.dto.bizview.WfBizViewData;
import com.forge.modules.workflow.dto.bizview.WfBizViewField;
import com.forge.modules.workflow.dto.bizview.WfBizViewResponse;
import com.forge.modules.workflow.framework.instance.WfInstanceAccessService;
import com.forge.modules.workflow.spi.WfBizViewProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 业务单据视图组装服务单元测试（路由/降级/鉴权/预览/附件越权）
 */
class WfBizViewServiceTest {

    private QueryService queryService;
    private WfInstanceAccessService accessService;
    private SysAttachmentService sysAttachmentService;
    private StubProvider provider;
    private WfBizViewService service;

    /** 测试桩 Provider：bizType=demo，视图/预览可注入行为 */
    static class StubProvider implements WfBizViewProvider {
        WfBizViewData data = new WfBizViewData();
        byte[] preview = new byte[]{1, 2, 3};
        boolean throwOnView = false;

        @Override
        public String bizType() {
            return "demo";
        }

        @Override
        public WfBizViewData getBizView(String bizId) {
            if (throwOnView) {
                throw new IllegalStateException("boom");
            }
            return data;
        }

        @Override
        public byte[] getBizPreview(String bizId) {
            return preview;
        }
    }

    @BeforeEach
    void setUp() {
        queryService = mock(QueryService.class);
        accessService = mock(WfInstanceAccessService.class);
        sysAttachmentService = mock(SysAttachmentService.class);
        provider = new StubProvider();
        provider.data.setTitle("演示单据");
        provider.data.setFields(List.of(new WfBizViewField("名称", "值")));
        service = new WfBizViewService(List.of(provider), queryService, accessService, sysAttachmentService);
        service.initProviderMap();

        FlwInstance instance = new FlwInstance();
        instance.setId(100L);
        instance.setCreateId("1");
        instance.setBusinessKey("demo:5");
        when(queryService.getInstance(100L)).thenReturn(instance);
        when(accessService.isParticipant(anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("SPI 命中：available=true 且字段/预览类型透出")
    void providerHit() {
        provider.data.setPreviewEnabled(true);
        WfBizViewResponse response = service.getBizView("100", 1L);
        assertTrue(response.isAvailable());
        assertEquals("demo", response.getBizType());
        assertEquals("5", response.getBizId());
        assertEquals("演示单据", response.getTitle());
        assertEquals(1, response.getFields().size());
        assertTrue(response.isPreviewEnabled());
        assertEquals("application/pdf", response.getPreviewContentType());
    }

    @Test
    @DisplayName("attachmentRef 命中附件域：清单装配")
    void attachmentsLoaded() {
        provider.data.setAttachmentRef(new WfBizViewAttachmentRef("contract", 5L));
        AttachmentResponse att = new AttachmentResponse();
        att.setId(9L);
        att.setOriginalName("扫描件.pdf");
        att.setFileSize(1024L);
        att.setFileExtension("pdf");
        when(sysAttachmentService.listByBiz("contract", 5L)).thenReturn(List.of(att));

        WfBizViewResponse response = service.getBizView("100", 1L);
        assertEquals(1, response.getAttachments().size());
        assertEquals("扫描件.pdf", response.getAttachments().get(0).getName());
    }

    @Test
    @DisplayName("bizType 无 Provider 注册：降级 available=false")
    void unknownBizTypeDegrades() {
        FlwInstance instance = new FlwInstance();
        instance.setBusinessKey("nosuch:1");
        when(queryService.getInstance(100L)).thenReturn(instance);
        assertFalse(service.getBizView("100", 1L).isAvailable());
    }

    @Test
    @DisplayName("businessKey 缺失/格式不合法：降级 available=false")
    void malformedBusinessKeyDegrades() {
        FlwInstance noKey = new FlwInstance();
        noKey.setBusinessKey(null);
        when(queryService.getInstance(100L)).thenReturn(noKey);
        assertFalse(service.getBizView("100", 1L).isAvailable());

        FlwInstance bad = new FlwInstance();
        bad.setBusinessKey("no-separator");
        when(queryService.getInstance(100L)).thenReturn(bad);
        assertFalse(service.getBizView("100", 1L).isAvailable());
    }

    @Test
    @DisplayName("Provider 异常/返回 null：降级 available=false 不上抛")
    void providerFailureDegrades() {
        provider.throwOnView = true;
        assertFalse(service.getBizView("100", 1L).isAvailable());

        provider.throwOnView = false;
        provider.data = null;
        assertFalse(service.getBizView("100", 1L).isAvailable());
    }

    @Test
    @DisplayName("非参与人：403")
    void nonParticipantForbidden() {
        when(accessService.isParticipant(100L, 2L)).thenReturn(false);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getBizView("100", 2L));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("预览：返回字节流与 Content-Type")
    void previewPayload() {
        WfBizViewService.BizPreviewPayload payload = service.getBizPreview("100", 1L);
        assertNotNull(payload);
        assertEquals(3, payload.content().length);
        assertEquals("application/pdf", payload.contentType());
    }

    @Test
    @DisplayName("附件下载：不属于单据附件域的 ID 拒绝（防跨实例探测）")
    void attachmentOutsideScopeRejected() {
        provider.data.setAttachmentRef(new WfBizViewAttachmentRef("contract", 5L));
        when(sysAttachmentService.listByBiz("contract", 5L)).thenReturn(List.of());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.downloadAttachment("100", 9L, 1L));
        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("附件下载：域内 ID 正常返回载荷")
    void attachmentWithinScopeDownloads() throws Exception {
        provider.data.setAttachmentRef(new WfBizViewAttachmentRef("contract", 5L));
        AttachmentResponse att = new AttachmentResponse();
        att.setId(9L);
        att.setOriginalName("扫描件.pdf");
        att.setFileType("application/pdf");
        when(sysAttachmentService.listByBiz("contract", 5L)).thenReturn(List.of(att));
        when(sysAttachmentService.openStream(eq(9L), eq(1L))).thenReturn(new AttachmentContent(
                new ByteArrayInputStream(new byte[]{9}), "扫描件.pdf", "application/pdf", 1L));

        WfBizViewService.AttachmentPayload payload = service.downloadAttachment("100", 9L, 1L);
        assertEquals("扫描件.pdf", payload.fileName());
        assertEquals(9, payload.content().readAllBytes()[0]);
    }

    @Test
    @DisplayName("bizType 冲突：初始化 fail-fast")
    void duplicateBizTypeFailsFast() {
        WfBizViewService conflicting = new WfBizViewService(
                List.of(provider, new StubProvider()), queryService, accessService, sysAttachmentService);
        assertThrows(IllegalStateException.class, conflicting::initProviderMap);
    }

    @Test
    @DisplayName("实例不存在：404")
    void instanceNotFound() {
        when(queryService.getInstance(999L)).thenReturn(null);
        when(queryService.getHistInstance(999L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getBizView("999", 1L));
        assertEquals(404, e.getCode());
    }
}
