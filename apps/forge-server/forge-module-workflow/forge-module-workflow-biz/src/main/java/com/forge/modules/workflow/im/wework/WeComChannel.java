package com.forge.modules.workflow.im.wework;

import com.forge.modules.workflow.im.AbstractTokenChannel;
import com.forge.modules.workflow.im.ImConfigReader;
import com.forge.modules.workflow.im.ImNotificationMessage;
import com.forge.modules.workflow.im.ImNotifyProperties;
import com.forge.modules.workflow.im.ImSendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信工作通知渠道：textcard 消息（点开跳转 forge-web）
 * <p>
 * API：GET /cgi-bin/gettoken（corpid+secret）→ POST /cgi-bin/message/send?access_token=
 * errcode=0 视为成功；invaliduser 非空记 warn（部分用户无效）；token 失效码强刷重发一次。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class WeComChannel extends AbstractTokenChannel {

    static final String TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send";

    private final ImNotifyProperties properties;
    private final ImConfigReader configReader;
    private final ObjectMapper objectMapper;
    private final RestOperations restTemplate;

    @Autowired
    public WeComChannel(ImNotifyProperties properties, ImConfigReader configReader, ObjectMapper objectMapper) {
        this(properties, configReader, objectMapper, defaultRestTemplate());
    }

    WeComChannel(ImNotifyProperties properties, ImConfigReader configReader,
                 ObjectMapper objectMapper, RestOperations restTemplate) {
        this.properties = properties;
        this.configReader = configReader;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    @Override
    public String channel() {
        return "wework";
    }

    @Override
    public boolean enabled() {
        return properties.weworkConfigured()
                && configReader.enabled()
                && configReader.channels().contains(channel());
    }

    @Override
    protected TokenFetch fetchToken() {
        String url = TOKEN_URL + "?corpid=" + properties.getWework().getCorpId()
                + "&corpsecret=" + properties.getWework().getSecret();
        String body = restTemplate.getForObject(url, String.class);
        var node = objectMapper.readTree(body);
        int errcode = node.path("errcode").asInt(0);
        if (errcode != 0) {
            throw new IllegalStateException("企微获取token失败: errcode=" + errcode
                    + ", errmsg=" + node.path("errmsg").asText());
        }
        return new TokenFetch(node.path("access_token").asText(), node.path("expires_in").asLong(7200));
    }

    @Override
    public ImSendResult sendToUser(String corpUserid, ImNotificationMessage message) {
        try {
            ImSendResult result = doSend(corpUserid, message, false);
            if (result.success()) {
                String invalid = result.invalidUser();
                if (StringUtils.hasText(invalid)) {
                    log.warn("企微通知部分用户无效: touser={}, invaliduser={}", corpUserid, invalid);
                }
                return ImSendResult.ok();
            }
            if (result.error() != null && result.error().startsWith("TOKEN_EXPIRED:")) {
                return doSend(corpUserid, message, true);
            }
            return result;
        } catch (Exception e) {
            log.warn("企微通知发送异常: touser={}, eventType={}, cause={}", corpUserid, message.eventType(), e.getMessage());
            return ImSendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * @param forceRefreshToken 先强刷 token（token 失效重试路径）
     * @return success=ok；失败时 error 为 "TOKEN_EXPIRED:errcode"（重试信号）或具体错误
     */
    private ImSendResult doSend(String corpUserid, ImNotificationMessage message, boolean forceRefreshToken) {
        int agentId;
        try {
            agentId = Integer.parseInt(properties.getWework().getAgentId());
        } catch (NumberFormatException e) {
            return ImSendResult.fail("agentId 非数字: " + properties.getWework().getAgentId());
        }
        try {
            String token = getToken(forceRefreshToken);
            Map<String, Object> textcard = new LinkedHashMap<>();
            textcard.put("title", message.title());
            textcard.put("description", message.content());
            textcard.put("url", message.url());
            textcard.put("btntxt", "查看详情");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("touser", corpUserid);
            payload.put("msgtype", "textcard");
            payload.put("agentid", agentId);
            payload.put("textcard", textcard);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String body = restTemplate.postForEntity(
                    SEND_URL + "?access_token=" + token,
                    new HttpEntity<>(objectMapper.writeValueAsString(payload), headers),
                    String.class).getBody();

            var node = objectMapper.readTree(body);
            int errcode = node.path("errcode").asInt(-1);
            if (isTokenExpired(errcode)) {
                return new ImSendResult(false, "TOKEN_EXPIRED:" + errcode, null);
            }
            if (errcode != 0) {
                return ImSendResult.fail("errcode=" + errcode + ", errmsg=" + node.path("errmsg").asText());
            }
            return new ImSendResult(true, null, node.path("invaliduser").asText(""));
        } catch (Exception e) {
            return ImSendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
