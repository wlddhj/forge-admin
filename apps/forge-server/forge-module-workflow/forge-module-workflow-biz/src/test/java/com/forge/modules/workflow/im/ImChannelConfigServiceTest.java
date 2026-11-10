package com.forge.modules.workflow.im;

import com.forge.common.exception.BusinessException;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.dto.im.ImChannelConfigRequest;
import com.forge.modules.workflow.dto.im.ImChannelConfigResponse;
import com.forge.modules.workflow.entity.WfImChannelConfig;
import com.forge.modules.workflow.mapper.WfImChannelConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IM 渠道配置服务单测：upsert 语义（唯一性/secret 空保留/校验）、出参脱敏、租户解析兜底
 */
class ImChannelConfigServiceTest {

    private WfImChannelConfigMapper mapper;
    private ImChannelConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(WfImChannelConfigMapper.class);
        service = new ImChannelConfigService(mapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private ImChannelConfigRequest request(String channel, Integer enabled,
                                           String corpId, String appKey, String agentId, String secret) {
        ImChannelConfigRequest request = new ImChannelConfigRequest();
        request.setTenantId(1L);
        request.setChannel(channel);
        request.setEnabled(enabled);
        request.setCorpId(corpId);
        request.setAppKey(appKey);
        request.setAgentId(agentId);
        request.setSecret(secret);
        return request;
    }

    private WfImChannelConfig existingRow(String channel) {
        WfImChannelConfig row = new WfImChannelConfig();
        row.setId(99L);
        row.setTenantId(1L);
        row.setChannel(channel);
        row.setEnabled(0);
        row.setAgentId("111");
        row.setSecret("old-secret");
        return row;
    }

    // ========== upsert ==========

    @Test
    void upsert_NewRow_RequiresSecret() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.upsert(request("wework", 1, "corp", null, "100", "")));
        verify(mapper, never()).insert(any(WfImChannelConfig.class));
    }

    @Test
    void upsert_NewRow_Inserts() {
        when(mapper.selectOne(any())).thenReturn(null);
        service.upsert(request("wework", 1, "corp001", null, "1000002", "s3cret"));
        ArgumentCaptor<WfImChannelConfig> captor = ArgumentCaptor.forClass(WfImChannelConfig.class);
        verify(mapper).insert(captor.capture());
        assertEquals("wework", captor.getValue().getChannel());
        assertEquals("s3cret", captor.getValue().getSecret());
        assertEquals(1L, captor.getValue().getTenantId());
    }

    @Test
    void upsert_ExistingRow_EmptySecretRetained() {
        when(mapper.selectOne(any())).thenReturn(existingRow("wework"));
        service.upsert(request("wework", 1, "corp001", null, "1000002", ""));
        ArgumentCaptor<WfImChannelConfig> captor = ArgumentCaptor.forClass(WfImChannelConfig.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("old-secret", captor.getValue().getSecret());
        verify(mapper, never()).insert(any(WfImChannelConfig.class));
    }

    @Test
    void upsert_UnsupportedChannel_Throws() {
        assertThrows(BusinessException.class, () ->
                service.upsert(request("telegram", 1, null, null, "100", "s")));
    }

    @Test
    void upsert_AgentIdNonNumeric_Throws() {
        assertThrows(BusinessException.class, () ->
                service.upsert(request("wework", 1, "corp", null, "abc", "s")));
    }

    @Test
    void upsert_WeworkWithoutCorpId_Throws() {
        assertThrows(BusinessException.class, () ->
                service.upsert(request("wework", 1, "", null, "100", "s")));
    }

    @Test
    void upsert_DingtalkWithoutAppKey_Throws() {
        assertThrows(BusinessException.class, () ->
                service.upsert(request("dingtalk", 1, null, "", "100", "s")));
    }

    // ========== 出参脱敏 ==========

    @Test
    void listByTenant_MasksSecret() {
        WfImChannelConfig row = existingRow("wework");
        row.setEnabled(1);
        row.setCorpId("corp001");
        when(mapper.selectList(any())).thenReturn(List.of(row));
        List<ImChannelConfigResponse> responses = service.listByTenant(1L);
        assertEquals(1, responses.size());
        ImChannelConfigResponse response = responses.get(0);
        assertEquals(99L, response.getId());
        assertTrue(response.getHasSecret());
        assertNull(response.getRemark());
        // 出参结构上没有 secret 字段（类型级保证），此处校验非空字段语义
        assertEquals("corp001", response.getCorpId());
    }

    @Test
    void listByTenant_EmptySecretRow_HasSecretFalse() {
        WfImChannelConfig row = existingRow("dingtalk");
        row.setSecret("");
        when(mapper.selectList(any())).thenReturn(List.of(row));
        assertFalse(service.listByTenant(1L).get(0).getHasSecret());
    }

    // ========== runnable ==========

    @Test
    void runnable_FalseWhenMissingOrDisabledOrIncomplete() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertFalse(service.runnable(1L, "wework"));

        WfImChannelConfig disabled = existingRow("wework");
        disabled.setEnabled(0);
        when(mapper.selectOne(any())).thenReturn(disabled);
        assertFalse(service.runnable(1L, "wework"));

        WfImChannelConfig incomplete = existingRow("wework");
        incomplete.setEnabled(1);
        incomplete.setCorpId(null);
        when(mapper.selectOne(any())).thenReturn(incomplete);
        assertFalse(service.runnable(1L, "wework"));

        WfImChannelConfig ok = existingRow("wework");
        ok.setEnabled(1);
        ok.setCorpId("corp001");
        ok.setAgentId("100");
        when(mapper.selectOne(any())).thenReturn(ok);
        assertTrue(service.runnable(1L, "wework"));
    }

    // ========== resolveTenantId ==========

    @Test
    void resolveTenantId_ContextValueWins() {
        TenantContextHolder.setTenantId(42L);
        assertEquals(42L, service.resolveTenantId());
    }

    @Test
    void resolveTenantId_NoContextDefaultsTo1() {
        TenantContextHolder.clear();
        assertEquals(1L, service.resolveTenantId());
    }
}
