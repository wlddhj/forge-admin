package com.forge.modules.workflow.im.dingtalk;

import com.forge.modules.workflow.entity.WfImChannelConfig;
import com.forge.modules.workflow.im.ImChannelConfigService;
import com.forge.modules.workflow.im.ImNotificationMessage;
import com.forge.modules.workflow.im.ImSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉钉渠道单测（租户化）：token 分租户缓存、link 报文、unionId→userid 按租户凭据转换与缓存、失败映射
 */
class DingTalkChannelTest {

    private RestOperations rest;
    private ImChannelConfigService configService;
    private DingTalkChannel channel;

    @BeforeEach
    void setUp() {
        configService = mock(ImChannelConfigService.class);
        rest = mock(RestOperations.class);
        channel = new DingTalkChannel(configService, new ObjectMapper(), rest);
    }

    private WfImChannelConfig config(long tenantId) {
        WfImChannelConfig config = new WfImChannelConfig();
        config.setTenantId(tenantId);
        config.setChannel("dingtalk");
        config.setEnabled(1);
        config.setAppKey("appkey-" + tenantId);
        config.setAgentId("8888888");
        config.setSecret("secret-x");
        return config;
    }

    private ImNotificationMessage message() {
        return new ImNotificationMessage("taskCreated", "新待办审批", "「测试流程」· 审批节点，请及时处理",
                "http://localhost:3000/workflow/task/todo?taskId=1", 1L, 2L, "test_process");
    }

    private void stubTokenOk() {
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"errcode\":0,\"access_token\":\"T1\",\"expires_in\":7200}");
    }

    @Test
    void send_NotConfigured_FailsFastWithoutHttp() {
        when(configService.configOf(9L, "dingtalk")).thenReturn(null);
        ImSendResult result = channel.sendToUser(9L, "manager1", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("未配置"));
        verify(rest, times(0)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void send_Ok_PayloadAndPerTenantTokenCached() {
        when(configService.configOf(1L, "dingtalk")).thenReturn(config(1L));
        when(configService.configOf(2L, "dingtalk")).thenReturn(config(2L));
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"errcode\":0,\"access_token\":\"T1\",\"expires_in\":7200}")
                .thenReturn("{\"errcode\":0,\"access_token\":\"T2\",\"expires_in\":7200}");
        when(rest.postForEntity(contains("asyncsend_v2"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\",\"task_id\":9}", HttpStatus.OK));

        assertTrue(channel.sendToUser(1L, "manager1", message()).success());
        assertTrue(channel.sendToUser(1L, "manager2", message()).success());
        assertTrue(channel.sendToUser(2L, "manager3", message()).success());

        // 租户1 命中缓存（一次 gettoken），租户2 独立取
        verify(rest, times(2)).getForObject(anyString(), eq(String.class));
        verify(rest).getForObject(contains("appkey=appkey-1"), eq(String.class));
        verify(rest).getForObject(contains("appkey=appkey-2"), eq(String.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                ArgumentCaptor.forClass((Class<HttpEntity<String>>) (Class<?>) HttpEntity.class);
        verify(rest, times(3)).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));
        var node = new ObjectMapper().readTree(entityCaptor.getAllValues().get(0).getBody());
        assertEquals(8888888, node.path("agent_id").asInt());
        assertEquals("manager1", node.path("userid_list").asText());
        assertEquals("link", node.path("msg").path("msgtype").asText());
        assertEquals("http://localhost:3000/workflow/task/todo?taskId=1",
                node.path("msg").path("link").path("messageUrl").asText());
    }

    @Test
    void send_TokenExpired_RetriesWithFreshToken() {
        when(configService.configOf(1L, "dingtalk")).thenReturn(config(1L));
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"errcode\":0,\"access_token\":\"T1\",\"expires_in\":7200}")
                .thenReturn("{\"errcode\":0,\"access_token\":\"T2\",\"expires_in\":7200}");
        when(rest.postForEntity(contains("asyncsend_v2"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":40014,\"errmsg\":\"invalid token\"}", HttpStatus.OK))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\"}", HttpStatus.OK));

        assertTrue(channel.sendToUser(1L, "manager1", message()).success());
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(rest, times(2)).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getAllValues().get(0), containsString("access_token=T1"));
        assertThat(urlCaptor.getAllValues().get(1), containsString("access_token=T2"));
    }

    @Test
    void resolveUseridByUnionId_TenantScopedAndCached() {
        when(configService.configOf(1L, "dingtalk")).thenReturn(config(1L));
        when(configService.configOf(2L, "dingtalk")).thenReturn(config(2L));
        stubTokenOk();
        when(rest.postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"result\":{\"userid\":\"userid-1\"}}",
                        HttpStatus.OK));

        assertEquals("userid-1", channel.resolveUseridByUnionId(1L, "union-1"));
        assertEquals("userid-1", channel.resolveUseridByUnionId(1L, "union-1"));
        // 第二次命中缓存，不再调转换接口
        verify(rest, times(1)).postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void resolveUseridByUnionId_FailureNotCached() {
        when(configService.configOf(1L, "dingtalk")).thenReturn(config(1L));
        stubTokenOk();
        when(rest.postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":60121,\"errmsg\":\"not found\"}", HttpStatus.OK));

        assertNull(channel.resolveUseridByUnionId(1L, "union-x"));
        assertNull(channel.resolveUseridByUnionId(1L, "union-x"));
        // 失败不缓存：两次都实际调用
        verify(rest, times(2)).postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void resolveUseridByUnionId_BlankShortCircuits() {
        assertNull(channel.resolveUseridByUnionId(1L, ""));
        verify(rest, never()).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void send_PlatformError_MappedAsFailure() {
        when(configService.configOf(1L, "dingtalk")).thenReturn(config(1L));
        stubTokenOk();
        when(rest.postForEntity(contains("asyncsend_v2"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":88,\"errmsg\":\"no permission\"}", HttpStatus.OK));

        ImSendResult result = channel.sendToUser(1L, "manager1", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("88"));
    }
}
