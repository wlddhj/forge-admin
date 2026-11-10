package com.forge.modules.workflow.im;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IM 消息推送配置属性（企业微信 / 钉钉工作通知）
 * <p>
 * 凭据走 yml 环境变量占位；运行时开关（总开关/渠道白名单/跳转域名）在 sys_config，
 * 由 {@link ImConfigReader} 读取——凭据缺失时对应渠道自动禁用，不影响启动与审批。
 * <p>
 * 设计见 .flow-neo/tasks/workflow-im-notify/02-design-plan.md
 *
 * @author forge-admin
 */
@Data
@Component
@ConfigurationProperties(prefix = "im.notify")
public class ImNotifyProperties {

    private Wework wework = new Wework();

    private Dingtalk dingtalk = new Dingtalk();

    /**
     * 企微凭据是否已配置
     */
    public boolean weworkConfigured() {
        Wework c = this.wework;
        return c != null && StringUtils.hasText(c.getCorpId())
                && StringUtils.hasText(c.getAgentId()) && StringUtils.hasText(c.getSecret());
    }

    /**
     * 钉钉凭据是否已配置
     */
    public boolean dingtalkConfigured() {
        Dingtalk c = this.dingtalk;
        return c != null && StringUtils.hasText(c.getAppKey())
                && StringUtils.hasText(c.getAppSecret()) && StringUtils.hasText(c.getAgentId());
    }

    @Data
    public static class Wework {
        /** 企业ID */
        private String corpId;
        /** 应用 agentId */
        private String agentId;
        /** 应用 secret */
        private String secret;
    }

    @Data
    public static class Dingtalk {
        /** 企业内部应用 appKey */
        private String appKey;
        /** 企业内部应用 appSecret */
        private String appSecret;
        /** 企业内部应用 agentId（工作通知 asyncsend_v2 必填） */
        private String agentId;
    }
}
