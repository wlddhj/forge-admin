package com.forge.modules.workflow.im.dingtalk;

import com.forge.modules.workflow.entity.WfImChannelConfig;
import com.forge.modules.workflow.im.AbstractTokenChannel;
import com.forge.modules.workflow.im.ImChannelConfigService;
import com.forge.modules.workflow.im.ImNotificationMessage;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钉钉工作通知渠道（按租户配置）：link 消息（asyncsend_v2）
 * <p>
 * API：GET /gettoken（appkey+appsecret）→ POST /topapi/message/corpconversation/asyncsend_v2；
 * userid 优先取 dingtalk_corp 手动绑定行，其次由既有 dingtalk 绑定的 unionId 经
 * /topapi/user/getbyunionid 转换（按租户凭据执行，转换结果进程内缓存，失败不缓存）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class DingTalkChannel extends AbstractTokenChannel {

    static final String TOKEN_URL = "https://oapi.dingtalk.com/gettoken";
    static final String SEND_URL = "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2";
    static final String GET_BY_UNIONID_URL = "https://oapi.dingtalk.com/topapi/user/getbyunionid";

    private final ImChannelConfigService configService;
    private final ObjectMapper objectMapper;
    private final RestOperations restTemplate;

    /**
     * (tenantId|unionId) → userid 转换缓存（钉钉侧映射稳定；失败不入缓存）
     */
    private final ConcurrentHashMap<String, String> unionidCache = new ConcurrentHashMap<>();

    @Autowired
    public DingTalkChannel(ImChannelConfigService configService, ObjectMapper objectMapper) {
        this(configService, objectMapper, defaultRestTemplate());
    }

    DingTalkChannel(ImChannelConfigService configService, ObjectMapper objectMapper, RestOperations restTemplate) {
        this.configService = configService;
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
        return "dingtalk";
    }

    @Override
    public boolean enabled(Long tenantId) {
        return configService.runnable(tenantId, channel());
    }

    @Override
    protected TokenFetch fetchToken(Long tenantId) {
        WfImChannelConfig config = requireConfig(tenantId);
        String url = TOKEN_URL + "?appkey=" + config.getAppKey() + "&appsecret=" + config.getSecret();
        String body = restTemplate.getForObject(url, String.class);
        var node = objectMapper.readTree(body);
        int errcode = node.path("errcode").asInt(0);
        if (errcode != 0) {
            throw new IllegalStateException("钉钉获取token失败: errcode=" + errcode
                    + ", errmsg=" + node.path("errmsg").asText());
        }
        return new TokenFetch(node.path("access_token").asText(), node.path("expires_in").asLong(7200));
    }

    @Override
    public ImSendResult sendToUser(Long tenantId, String corpUserid, ImNotificationMessage message) {
        WfImChannelConfig config = configService.configOf(tenantId, channel());
        if (!isReady(config)) {
            return ImSendResult.fail("渠道未配置或未启用");
        }
        int agentId;
        try {
            agentId = Integer.parseInt(config.getAgentId());
        } catch (NumberFormatException e) {
            return ImSendResult.fail("agentId 非数字: " + config.getAgentId());
        }
        try {
            ImSendResult result = doSend(config, agentId, corpUserid, message, false);
            if (result.success()) {
                return ImSendResult.ok();
            }
            if (result.error() != null && result.error().startsWith("TOKEN_EXPIRED:")) {
                return doSend(config, agentId, corpUserid, message, true);
            }
            return result;
        } catch (Exception e) {
            log.warn("钉钉通知发送异常: tenantId={}, userid={}, eventType={}, cause={}",
                    tenantId, corpUserid, message.eventType(), e.getMessage());
            return ImSendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * unionId → 钉钉 userid（按租户凭据换取；缓存优先；失败返回 null 不缓存）
     */
    public String resolveUseridByUnionId(Long tenantId, String unionId) {
        if (!StringUtils.hasText(unionId)) {
            return null;
        }
        String cacheKey = tenantId + "|" + unionId;
        String cached = unionidCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            String token = getToken(tenantId, false);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("unionid", unionId);
            String body = postJson(GET_BY_UNIONID_URL + "?access_token=" + token, payload);
            var node = objectMapper.readTree(body);
            if (node.path("errcode").asInt(-1) != 0) {
                log.warn("钉钉unionId转换userid失败: tenantId={}, errcode={}, errmsg={}",
                        tenantId, node.path("errcode").asInt(), node.path("errmsg").asText());
                return null;
            }
            String userid = node.path("result").path("userid").asText(null);
            if (StringUtils.hasText(userid)) {
                unionidCache.putIfAbsent(cacheKey, userid);
            }
            return userid;
        } catch (Exception e) {
            log.warn("钉钉unionId转换userid异常: tenantId={}, cause={}", tenantId, e.getMessage());
            return null;
        }
    }

    private WfImChannelConfig requireConfig(Long tenantId) {
        WfImChannelConfig config = configService.configOf(tenantId, channel());
        if (!isReady(config)) {
            throw new IllegalStateException("钉钉渠道未配置或未启用: tenantId=" + tenantId);
        }
        return config;
    }

    private boolean isReady(WfImChannelConfig config) {
        return config != null && config.getEnabled() != null && config.getEnabled() == 1
                && StringUtils.hasText(config.getAppKey())
                && StringUtils.hasText(config.getAgentId()) && StringUtils.hasText(config.getSecret());
    }

    private ImSendResult doSend(WfImChannelConfig config, int agentId, String corpUserid,
                                ImNotificationMessage message, boolean forceRefreshToken) {
        try {
            String token = getToken(config.getTenantId(), forceRefreshToken);

            Map<String, Object> link = new LinkedHashMap<>();
            link.put("title", message.title());
            link.put("text", message.content());
            link.put("messageUrl", message.url());
            link.put("picUrl", "");
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("msgtype", "link");
            msg.put("link", link);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agent_id", agentId);
            payload.put("userid_list", corpUserid);
            payload.put("msg", msg);

            String body = postJson(SEND_URL + "?access_token=" + token, payload);
            var node = objectMapper.readTree(body);
            int errcode = node.path("errcode").asInt(-1);
            if (isTokenExpired(errcode)) {
                return new ImSendResult(false, "TOKEN_EXPIRED:" + errcode, null);
            }
            if (errcode != 0) {
                return ImSendResult.fail("errcode=" + errcode + ", errmsg=" + node.path("errmsg").asText());
            }
            return ImSendResult.ok();
        } catch (Exception e) {
            return ImSendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String postJson(String url, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url,
                new HttpEntity<>(objectMapper.writeValueAsString(payload), headers), String.class).getBody();
    }
}
