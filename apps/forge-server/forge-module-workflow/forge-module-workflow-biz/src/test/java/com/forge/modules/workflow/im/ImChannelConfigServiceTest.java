package com.forge.modules.workflow.im;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forge.common.exception.BusinessException;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.workflow.dto.im.ImChannelConfigRequest;
import com.forge.modules.workflow.dto.im.ImChannelConfigResponse;
import com.forge.modules.workflow.entity.WfImChannelConfig;
import com.forge.modules.workflow.mapper.WfImChannelConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IM 渠道配置服务单测：upsert 语义（唯一性/secret 空保留/校验/并发撞唯一键收敛）、
 * 删除墓碑惯用法、出参脱敏、租户解析 fail-closed
 */
class ImChannelConfigServiceTest {

    private WfImChannelConfigMapper mapper;
    private ImChannelConfigService service;

    @BeforeAll
    static void initTableInfo() {
        // MP 3.5.18：纯单测下 LambdaUpdateWrapper 渲染（getSqlSegment）需要实体 TableInfo 缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfImChannelConfig.class);
    }

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

    @Test
    void upsert_ConcurrentInsertDuplicateKey_ConvergesToUpdate() {
        // 并发双保存同 (tenant, channel)：双方首查都判空（读的是彼此 insert 前的快照），
        // 赢家 insert 成功，输家撞 uk_tenant_channel → 重查存量行转更新，不抛错、不落第二行
        WfImChannelConfig winnerRow = existingRow("wework");
        when(mapper.selectOne(any())).thenReturn(null, null, winnerRow);
        when(mapper.insert(any(WfImChannelConfig.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("uk_tenant_channel"));

        // 赢家
        service.upsert(request("wework", 1, "corp001", null, "1000002", "winner-secret"));
        // 输家：不抛错，走重查更新
        service.upsert(request("wework", 1, "corp002", null, "1000002", "loser-secret"));

        verify(mapper, times(2)).insert(any(WfImChannelConfig.class));
        ArgumentCaptor<WfImChannelConfig> captor = ArgumentCaptor.forClass(WfImChannelConfig.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("loser-secret", captor.getValue().getSecret(), "输家凭据收敛到赢家行");
        assertEquals("corp002", captor.getValue().getCorpId());
        assertEquals(99L, captor.getValue().getId(), "更新的是赢家落库的存量行");
    }

    @Test
    void upsert_DuplicateKeyButRowStillMissing_Rethrows() {
        // 撞唯一键却重查不到行：非常规异常（连接故障等），按原语义抛出而非吞掉
        when(mapper.selectOne(any())).thenReturn(null, null);
        when(mapper.insert(any(WfImChannelConfig.class)))
                .thenThrow(new DuplicateKeyException("uk_tenant_channel"));

        assertThrows(DuplicateKeyException.class,
                () -> service.upsert(request("wework", 1, "corp001", null, "1000002", "s3cret")));
        verify(mapper, never()).updateById(any(WfImChannelConfig.class));
    }

    @Test
    void deleteConfig_TombstoneUsesRowIdNotOne() {
        // 墓碑惯用法：deleted 置本行 id（配合 uk 含 deleted 列，同键多墓碑不互撞）。
        // id 取大值（>127）：H2 列已是 BIGINT，同时防回归到旧 TINYINT/墓碑=1 形态
        service.deleteConfig(130_072L);

        verify(mapper, never()).deleteById(any(Long.class));
        ArgumentCaptor<LambdaUpdateWrapper<WfImChannelConfig>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(130_072L),
                "deleted 置值与行条件均命中目标行 id");
        assertTrue(captor.getValue().getSqlSet().contains("deleted"),
                "SET 子句写 deleted 列（墓碑置 id）");
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

    @Test
    void runnable_NullTenant_FailClosedWithoutQuery() {
        // 无租户来源：渠道直接视为不可用（fail-closed 不发送），且不查库
        assertFalse(service.runnable(null, "wework"));
        verify(mapper, never()).selectOne(any());
    }

    // ========== resolveTenantId ==========

    @Test
    void resolveTenantId_ContextValueWins() {
        TenantContextHolder.setTenantId(42L);
        assertEquals(42L, service.resolveTenantId());
    }

    @Test
    void resolveTenantId_NoContextReturnsNull() {
        // 去掉默认租户兜底：无上下文返回 null，由调用方 fail-closed
        TenantContextHolder.clear();
        assertNull(service.resolveTenantId());
    }
}
