package com.forge.modules.workflow.im;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * IM 通知路由器（按租户）：解析租户 → 过滤启用渠道 → 解析用户绑定 → 逐用户逐渠道扇出。
 * <p>
 * 失败语义（对齐 ReliableEventConsumer 重试契约）：
 * <ul>
 *   <li>无启用渠道 / 无目标用户：no-op 直接返回（不抛，避免功能关闭时审计表噪音）</li>
 *   <li>单渠道失败、其他渠道有成功：warn 日志，不抛（重试会给成功渠道重复推送）</li>
 *   <li>全部启用渠道零成功：抛 IllegalStateException，交由消费模板退避重试</li>
 * </ul>
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ImNotificationRouter {

    private final List<ImNotificationChannel> channels;
    private final ImUserBindingResolver bindingResolver;
    private final ImChannelConfigService configService;

    public ImNotificationRouter(List<ImNotificationChannel> channels,
                                ImUserBindingResolver bindingResolver,
                                ImChannelConfigService configService) {
        this.channels = channels;
        this.bindingResolver = bindingResolver;
        this.configService = configService;
    }

    /**
     * 扇出一条通知给一批系统用户（该租户的全部启用渠道）
     *
     * @param message 消息
     * @param userIds 系统用户ID集合（可空）
     * @throws IllegalStateException 所有启用渠道均未成功送达任何用户
     */
    public void dispatch(ImNotificationMessage message, Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        Long tenantId = configService.resolveTenantId();
        List<ImNotificationChannel> enabled = channels.stream()
                .filter(channel -> channel.enabled(tenantId))
                .toList();
        if (enabled.isEmpty()) {
            return;
        }

        int anySuccess = 0;
        int totalFailed = 0;
        for (ImNotificationChannel channel : enabled) {
            Map<Long, String> bindings = bindingResolver.resolve(channel.channel(), tenantId, userIds);
            int skippedUnbound = userIds.size() - bindings.size();
            int sent = 0;
            int failed = 0;
            String lastError = null;
            for (Map.Entry<Long, String> binding : bindings.entrySet()) {
                ImSendResult result = channel.sendToUser(tenantId, binding.getValue(), message);
                if (result.success()) {
                    sent++;
                } else {
                    failed++;
                    lastError = result.error();
                    log.warn("IM通知发送失败: channel={}, tenantId={}, userId={}, corpUserid={}, eventType={}, error={}",
                            channel.channel(), tenantId, binding.getKey(), binding.getValue(),
                            message.eventType(), result.error());
                }
            }
            anySuccess += sent;
            totalFailed += failed;
            log.info("IM通知投递: channel={}, tenantId={}, eventType={}, total={}, sent={}, skippedUnbound={}, failed={}",
                    channel.channel(), tenantId, message.eventType(), userIds.size(), sent, skippedUnbound, failed);
            if (lastError != null && sent == 0 && !bindings.isEmpty()) {
                log.warn("IM渠道 {} 全部用户发送失败: tenantId={}, eventType={}, error={}",
                        channel.channel(), tenantId, message.eventType(), lastError);
            }
        }

        if (anySuccess == 0 && totalFailed > 0) {
            throw new IllegalStateException("IM通知全部渠道投递失败: eventType=" + message.eventType()
                    + ", tenantId=" + tenantId + ", instanceId=" + message.instanceId()
                    + ", failedChannels=" + enabled.size());
        }
    }
}
