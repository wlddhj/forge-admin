package com.forge.modules.workflow.im;

/**
 * IM 通知渠道 SPI（按租户维度）：企业微信 / 钉钉各实现一个 Provider。
 * <p>
 * 渠道配置（凭据/agentId/启停）按租户存 wf_im_channel_config，经
 * {@link ImChannelConfigService} 读取；实现自管 access_token 缓存（分租户）。
 * 发送失败以 {@link ImSendResult} 表达，由 {@link ImNotificationRouter} 汇总。
 * 接口保持与审批回写（模式 B）复用面兼容：sendToUser 之外后续可挂回调关联回写。
 *
 * @author forge-admin
 */
public interface ImNotificationChannel {

    /**
     * 渠道标识：wework / dingtalk
     */
    String channel();

    /**
     * 渠道在该租户下是否可推送（已配置 + 启用 + 凭据完整）
     */
    boolean enabled(Long tenantId);

    /**
     * 发送工作通知给单个企业用户（未配置/禁用时返回失败，不发生网络调用）
     *
     * @param tenantId   租户ID（决定用哪份渠道配置）
     * @param corpUserid 企业内用户标识（企微 userid / 钉钉 userid）
     * @param message    消息
     */
    ImSendResult sendToUser(Long tenantId, String corpUserid, ImNotificationMessage message);
}
