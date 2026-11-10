package com.forge.modules.workflow.im.wework;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企微渠道单测（租户化）：token 分租户缓存/失效强刷重发、textcard 报文、失败映射、未配置快速失败
 */
class WeComChannelTest {

    private RestOperations rest;
    private ImChannelConfigService configService;
    private WeComChannel channel;

    @BeforeEach
    void setUp() {
        configService = mock(ImChannelConfigService.class);
        rest = mock(RestOperations.class);
        channel = new WeComChannel(configService, new ObjectMapper(), rest);
    }

    private WfImChannelConfig config(long tenantId) {
        WfImChannelConfig config = new WfImChannelConfig();
        config.setTenantId(tenantId);
        config.setChannel("wework");
        config.setEnabled(1);
        config.setCorpId("corp00" + tenantId);
        config.setAgentId("1000002");
        config.setSecret("secret-x");
        return config;
    }

    private ImNotificationMessage message() {
        return new ImNotificationMessage("taskCreated", "新待办审批", "「测试流程」· 审批节点，请及时处理",
                "http://localhost:3000/workflow/task/todo?taskId=1", 1L, 2L, "test_process");
    }

    @Test
    void enabled_RequiresRunnableConfig() {
        when(configService.runnable(1L, "wework")).thenReturn(true);
        when(configService.runnable(2L, "wework")).thenReturn(false);
        assertTrue(channel.enabled(1L));
        assertFalse(channel.enabled(2L));
    }

    @Test
    void send_NotConfigured_FailsFastWithoutHttp() {
        when(configService.configOf(9L, "wework")).thenReturn(null);
        ImSendResult result = channel.sendToUser(9L, "zhangsan", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("未配置"));
        verify(rest, times(0)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void send_Ok_PayloadAndPerTenantTokenCached() {
        when(configService.configOf(1L, "wework")).thenReturn(config(1L));
        when(configService.configOf(2L, "wework")).thenReturn(config(2L));
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"access_token\":\"T1\",\"expires_in\":7200,\"errcode\":0}")
                .thenReturn("{\"access_token\":\"T2\",\"expires_in\":7200,\"errcode\":0}");
        when(rest.postForEntity(contains("message/send"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\",\"invaliduser\":\"\"}",
                        HttpStatus.OK));

        assertTrue(channel.sendToUser(1L, "zhangsan", message()).success());
        assertTrue(channel.sendToUser(1L, "lisi", message()).success());
        assertTrue(channel.sendToUser(2L, "wangwu", message()).success());

        // 租户1 token 缓存命中（仅取一次），租户2 独立取 token
        verify(rest, times(2)).getForObject(anyString(), eq(String.class));
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(rest, times(3)).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getAllValues().get(0), containsString("access_token=T1"));
        assertThat(urlCaptor.getAllValues().get(2), containsString("access_token=T2"));
        // 凭据按租户隔离：租户1 的 gettoken 用 corp001
        verify(rest).getForObject(contains("corpid=corp001"), eq(String.class));
        verify(rest).getForObject(contains("corpid=corp002"), eq(String.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                ArgumentCaptor.forClass((Class<HttpEntity<String>>) (Class<?>) HttpEntity.class);
        verify(rest, times(3)).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));
        var node = new ObjectMapper().readTree(entityCaptor.getAllValues().get(0).getBody());
        assertEquals("zhangsan", node.path("touser").asText());
        assertEquals("textcard", node.path("msgtype").asText());
        assertEquals(1000002, node.path("agentid").asInt());
        assertEquals("新待办审批", node.path("textcard").path("title").asText());
    }

    @Test
    void send_TokenExpired_RetriesWithFreshToken() {
        when(configService.configOf(1L, "wework")).thenReturn(config(1L));
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"access_token\":\"T1\",\"expires_in\":7200,\"errcode\":0}")
                .thenReturn("{\"access_token\":\"T2\",\"expires_in\":7200,\"errcode\":0}");
        when(rest.postForEntity(contains("message/send"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":42001,\"errmsg\":\"token expired\"}", HttpStatus.OK))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\"}", HttpStatus.OK));

        assertTrue(channel.sendToUser(1L, "zhangsan", message()).success());

        verify(rest, times(2)).getForObject(anyString(), eq(String.class));
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(rest, times(2)).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getAllValues().get(0), containsString("access_token=T1"));
        assertThat(urlCaptor.getAllValues().get(1), containsString("access_token=T2"));
    }

    @Test
    void send_PlatformError_MappedAsFailure() {
        when(configService.configOf(1L, "wework")).thenReturn(config(1L));
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"access_token\":\"T1\",\"expires_in\":7200,\"errcode\":0}");
        when(rest.postForEntity(contains("message/send"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":81013,\"errmsg\":\"user not found\"}", HttpStatus.OK));

        ImSendResult result = channel.sendToUser(1L, "ghost", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("81013"));
    }

    @Test
    void send_NetworkException_MappedAsFailure_NoThrow() {
        when(configService.configOf(1L, "wework")).thenReturn(config(1L));
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenThrow(new IllegalStateException("connect refused"));
        ImSendResult result = channel.sendToUser(1L, "zhangsan", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("connect refused"));
    }

    @Test
    void send_InvalidAgentId_FailsWithoutHttp() {
        WfImChannelConfig bad = config(1L);
        bad.setAgentId("not-a-number");
        when(configService.configOf(1L, "wework")).thenReturn(bad);
        ImSendResult result = channel.sendToUser(1L, "zhangsan", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("agentId"));
        verify(rest, times(0)).getForObject(anyString(), eq(String.class));
    }
}
