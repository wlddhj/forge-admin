package com.forge.modules.workflow.im;

/**
 * IM 通知渠道 SPI：企业微信 / 钉钉各实现一个 Provider。
 * <p>
 * 渠道自管 access_token 获取与缓存；实现不得抛出未受检异常以外的中断行为——
 * 发送失败以 {@link ImSendResult} 表达，由 {@link ImNotificationRouter} 汇总。
 * 接口保持与审批回写（模式 B）复用面兼容：sendToUser 之外后续可挂回调关联回写。
 *
 * @author forge-admin
 */
public interface ImNotificationChannel {

    /**
     * 渠道标识（sys_config im.notify.channels 白名单键）：wework / dingtalk
     */
    String channel();

    /**
     * 渠道是否启用：凭据已配置（yml） && sys_config 总开关 && 白名单包含本渠道
     */
    boolean enabled();

    /**
     * 发送工作通知给单个企业用户
     *
     * @param corpUserid 企业内用户标识（企微 userid / 钉钉 userid）
     * @param message    消息
     * @return 发送结果（不抛异常表达失败）
     */
    ImSendResult sendToUser(String corpUserid, ImNotificationMessage message);
}
