package com.forge.modules.workflow.im;

import com.forge.modules.system.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * IM 推送运行时开关读取器（sys_config）
 * <p>
 * 键：im.notify.enabled（默认 true）/ im.notify.channels（默认 wework,dingtalk）/
 * im.notify.web-base-url（默认空）。凭据本身在 yml（ImNotifyProperties），密钥不入库。
 * <p>
 * 用 {@link ObjectProvider} 惰性解析 SysConfigService：测试上下文（WorkflowTestApplication
 * 只扫 workflow 包）无该 bean 时静默退化为默认值；生产全量上下文正常注入。
 * 异步线程无租户上下文安全：sys_config 无租户隔离（已核实实体无 tenantId）。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ImConfigReader {

    static final String KEY_ENABLED = "im.notify.enabled";
    static final String KEY_CHANNELS = "im.notify.channels";
    static final String KEY_WEB_BASE_URL = "im.notify.web-base-url";

    static final boolean DEFAULT_ENABLED = true;
    static final List<String> DEFAULT_CHANNELS = List.of("wework", "dingtalk");

    private final ObjectProvider<SysConfigService> sysConfigService;

    public ImConfigReader(ObjectProvider<SysConfigService> sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 总开关是否打开
     */
    public boolean enabled() {
        String value = safeGet(KEY_ENABLED);
        if (value == null) {
            return DEFAULT_ENABLED;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    /**
     * 渠道白名单
     */
    public List<String> channels() {
        String value = safeGet(KEY_CHANNELS);
        if (!StringUtils.hasText(value)) {
            return DEFAULT_CHANNELS;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 消息跳转域名（无尾斜杠；未配置返回空串）
     */
    public String webBaseUrl() {
        String value = safeGet(KEY_WEB_BASE_URL);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /**
     * 单键读取：无 bean / 查询异常 一律返回 null 走默认值
     */
    private String safeGet(String key) {
        SysConfigService service = sysConfigService.getIfAvailable();
        if (service == null) {
            return null;
        }
        try {
            return service.getConfigValueByKey(key);
        } catch (Exception e) {
            log.warn("sys_config 读取失败，IM 推送开关走默认值: key={}, cause={}", key, e.getMessage());
            return null;
        }
    }
}
