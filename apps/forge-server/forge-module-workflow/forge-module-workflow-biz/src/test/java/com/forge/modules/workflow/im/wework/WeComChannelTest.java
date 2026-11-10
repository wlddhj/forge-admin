package com.forge.modules.workflow.im.wework;

import com.forge.modules.workflow.im.ImConfigReader;
import com.forge.modules.workflow.im.ImNotificationMessage;
import com.forge.modules.workflow.im.ImNotifyProperties;
import com.forge.modules.workflow.im.ImSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

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
 * 企微渠道单测：token 获取/缓存/失效强刷重发、textcard 报文、失败映射
 */
class WeComChannelTest {

    private RestOperations rest;
    private WeComChannel channel;

    @BeforeEach
    void setUp() {
        ImNotifyProperties props = new ImNotifyProperties();
        props.getWework().setCorpId("corp001");
        props.getWework().setAgentId("1000002");
        props.getWework().setSecret("secret-x");

        ImConfigReader reader = mock(ImConfigReader.class);
        when(reader.enabled()).thenReturn(true);
        when(reader.channels()).thenReturn(List.of("wework", "dingtalk"));

        rest = mock(RestOperations.class);
        channel = new WeComChannel(props, reader, new ObjectMapper(), rest);
    }

    private ImNotificationMessage message() {
        return new ImNotificationMessage("taskCreated", "新待办审批", "「测试流程」· 审批节点，请及时处理",
                "http://localhost:3000/workflow/task/todo?taskId=1", 1L, 2L, "test_process");
    }

    @Test
    void enabled_RequiresCredentialsAndConfig() {
        ImNotifyProperties emptyProps = new ImNotifyProperties();
        ImConfigReader reader = mock(ImConfigReader.class);
        when(reader.enabled()).thenReturn(true);
        when(reader.channels()).thenReturn(List.of("wework"));
        WeComChannel unconfigured = new WeComChannel(emptyProps, reader, new ObjectMapper(),
                new RestTemplate());
        assertFalse(unconfigured.enabled());
        assertTrue(channel.enabled());
    }

    @Test
    void send_Ok_PayloadAndTokenCached() {
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"access_token\":\"T1\",\"expires_in\":7200,\"errcode\":0}");
        when(rest.postForEntity(contains("message/send"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\",\"invaliduser\":\"\"}",
                        HttpStatus.OK));

        assertTrue(channel.sendToUser("zhangsan", message()).success());
        assertTrue(channel.sendToUser("lisi", message()).success());

        // token 只取一次（第二条走缓存）
        verify(rest, times(1)).getForObject(anyString(), eq(String.class));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(rest, times(2)).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getAllValues().get(0), containsString("access_token=T1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                ArgumentCaptor.forClass((Class<HttpEntity<String>>) (Class<?>) HttpEntity.class);
        verify(rest, times(2)).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));
        var node = new ObjectMapper().readTree(entityCaptor.getAllValues().get(0).getBody());
        assertEquals("zhangsan", node.path("touser").asText());
        assertEquals("textcard", node.path("msgtype").asText());
        assertEquals(1000002, node.path("agentid").asInt());
        assertEquals("新待办审批", node.path("textcard").path("title").asText());
        assertEquals("查看详情", node.path("textcard").path("btntxt").asText());
    }

    @Test
    void send_TokenExpired_RetriesWithFreshToken() {
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"access_token\":\"T1\",\"expires_in\":7200,\"errcode\":0}")
                .thenReturn("{\"access_token\":\"T2\",\"expires_in\":7200,\"errcode\":0}");
        when(rest.postForEntity(contains("message/send"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":42001,\"errmsg\":\"token expired\"}", HttpStatus.OK))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\"}", HttpStatus.OK));

        assertTrue(channel.sendToUser("zhangsan", message()).success());

        verify(rest, times(2)).getForObject(anyString(), eq(String.class));
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(rest, times(2)).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getAllValues().get(0), containsString("access_token=T1"));
        assertThat(urlCaptor.getAllValues().get(1), containsString("access_token=T2"));
    }

    @Test
    void send_PlatformError_MappedAsFailure() {
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"access_token\":\"T1\",\"expires_in\":7200,\"errcode\":0}");
        when(rest.postForEntity(contains("message/send"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":81013,\"errmsg\":\"user not found\"}", HttpStatus.OK));

        ImSendResult result = channel.sendToUser("ghost", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("81013"));
    }

    @Test
    void send_NetworkException_MappedAsFailure_NoThrow() {
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenThrow(new IllegalStateException("connect refused"));
        ImSendResult result = channel.sendToUser("zhangsan", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("connect refused"));
    }

    @Test
    void send_InvalidAgentId_FailsWithoutHttp() {
        ImNotifyProperties props = new ImNotifyProperties();
        props.getWework().setCorpId("corp001");
        props.getWework().setAgentId("not-a-number");
        props.getWework().setSecret("s");
        ImConfigReader reader = mock(ImConfigReader.class);
        WeComChannel badAgent = new WeComChannel(props, reader, new ObjectMapper(), rest);
        ImSendResult result = badAgent.sendToUser("zhangsan", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("agentId"));
    }
}
