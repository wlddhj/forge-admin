package com.forge.modules.workflow.im.dingtalk;

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
 * 钉钉渠道单测：token 缓存、link 报文、unionId→userid 转换与缓存、失败映射
 */
class DingTalkChannelTest {

    private RestOperations rest;
    private DingTalkChannel channel;

    @BeforeEach
    void setUp() {
        ImNotifyProperties props = new ImNotifyProperties();
        props.getDingtalk().setAppKey("appkey-x");
        props.getDingtalk().setAppSecret("secret-x");
        props.getDingtalk().setAgentId("8888888");

        ImConfigReader reader = mock(ImConfigReader.class);
        when(reader.enabled()).thenReturn(true);
        when(reader.channels()).thenReturn(List.of("wework", "dingtalk"));

        rest = mock(RestOperations.class);
        channel = new DingTalkChannel(props, reader, new ObjectMapper(), rest);
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
    void send_Ok_PayloadAndTokenCached() {
        stubTokenOk();
        when(rest.postForEntity(contains("asyncsend_v2"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\",\"task_id\":9}", HttpStatus.OK));

        assertTrue(channel.sendToUser("manager1", message()).success());
        assertTrue(channel.sendToUser("manager2", message()).success());
        verify(rest, times(1)).getForObject(anyString(), eq(String.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                ArgumentCaptor.forClass((Class<HttpEntity<String>>) (Class<?>) HttpEntity.class);
        verify(rest, times(2)).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));
        var node = new ObjectMapper().readTree(entityCaptor.getAllValues().get(0).getBody());
        assertEquals(8888888, node.path("agent_id").asInt());
        assertEquals("manager1", node.path("userid_list").asText());
        assertEquals("link", node.path("msg").path("msgtype").asText());
        assertEquals("新待办审批", node.path("msg").path("link").path("title").asText());
        assertEquals("http://localhost:3000/workflow/task/todo?taskId=1",
                node.path("msg").path("link").path("messageUrl").asText());
    }

    @Test
    void send_TokenExpired_RetriesWithFreshToken() {
        when(rest.getForObject(contains("gettoken"), eq(String.class)))
                .thenReturn("{\"errcode\":0,\"access_token\":\"T1\",\"expires_in\":7200}")
                .thenReturn("{\"errcode\":0,\"access_token\":\"T2\",\"expires_in\":7200}");
        when(rest.postForEntity(contains("asyncsend_v2"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":40014,\"errmsg\":\"invalid token\"}", HttpStatus.OK))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\"}", HttpStatus.OK));

        assertTrue(channel.sendToUser("manager1", message()).success());
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(rest, times(2)).postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(String.class));
        assertThat(urlCaptor.getAllValues().get(0), containsString("access_token=T1"));
        assertThat(urlCaptor.getAllValues().get(1), containsString("access_token=T2"));
    }

    @Test
    void resolveUseridByUnionId_CachesConversion() {
        stubTokenOk();
        when(rest.postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"result\":{\"userid\":\"userid-1\"}}",
                        HttpStatus.OK));

        assertEquals("userid-1", channel.resolveUseridByUnionId("union-1"));
        assertEquals("userid-1", channel.resolveUseridByUnionId("union-1"));
        // 第二次命中缓存，不再调转换接口
        verify(rest, times(1)).postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void resolveUseridByUnionId_FailureNotCached() {
        stubTokenOk();
        when(rest.postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":60121,\"errmsg\":\"not found\"}", HttpStatus.OK));

        assertNull(channel.resolveUseridByUnionId("union-x"));
        assertNull(channel.resolveUseridByUnionId("union-x"));
        // 失败不缓存：两次都实际调用
        verify(rest, times(2)).postForEntity(contains("getbyunionid"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void resolveUseridByUnionId_BlankShortCircuits() {
        assertNull(channel.resolveUseridByUnionId(""));
        verify(rest, never()).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void send_PlatformError_MappedAsFailure() {
        stubTokenOk();
        when(rest.postForEntity(contains("asyncsend_v2"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":88,\"errmsg\":\"no permission\"}", HttpStatus.OK));

        ImSendResult result = channel.sendToUser("manager1", message());
        assertFalse(result.success());
        assertThat(result.error(), containsString("88"));
    }
}
