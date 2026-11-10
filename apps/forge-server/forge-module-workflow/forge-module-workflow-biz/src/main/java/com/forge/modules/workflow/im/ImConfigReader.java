package com.forge.modules.workflow.im;

import com.forge.modules.system.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IM 推送平台级配置读取器（sys_config）
 * <p>
 * 仅承载非租户差异项：im.notify.web-base-url（消息跳转域名，单一前端部署属性）。
 * 渠道凭据与启停按租户存 wf_im_channel_config（ImChannelConfigService），
 * v1 的 im.notify.enabled / im.notify.channels 键已废弃。
 * <p>
 * 用 {@link ObjectProvider} 惰性解析 SysConfigService：测试上下文（WorkflowTestApplication
 * 只扫 workflow 包）无该 bean 时静默退化为默认值。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ImConfigReader {

    static final String KEY_WEB_BASE_URL = "im.notify.web-base-url";

    private final ObjectProvider<SysConfigService> sysConfigService;

    public ImConfigReader(ObjectProvider<SysConfigService> sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 消息跳转域名（无尾斜杠；未配置返回空串）
     */
    public String webBaseUrl() {
        SysConfigService service = sysConfigService.getIfAvailable();
        if (service == null) {
            return "";
        }
        String value;
        try {
            value = service.getConfigValueByKey(KEY_WEB_BASE_URL);
        } catch (Exception e) {
            log.warn("sys_config 读取失败，IM 跳转域名走默认值: key={}, cause={}", KEY_WEB_BASE_URL, e.getMessage());
            return "";
        }
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
