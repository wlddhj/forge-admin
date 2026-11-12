package com.forge.common.config;

import com.forge.modules.system.auth.properties.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@EnableConfigurationProperties({
    CaptchaProperties.class,
    PasswordPolicyProperties.class,
    LoginPolicyProperties.class,
    UploadPolicyProperties.class
})
@ConfigurationProperties(prefix = "forge.security")
public class SecurityProperties {
    private CaptchaProperties captcha = new CaptchaProperties();
    private PasswordPolicyProperties password = new PasswordPolicyProperties();
    private LoginPolicyProperties login = new LoginPolicyProperties();
    private UploadPolicyProperties upload = new UploadPolicyProperties();
    /**
     * CORS 允许的源（显式白名单）。
     * 默认空=未配置：dev 环境回退本机前端地址，其余环境不注册任何跨域源；
     * 配置 "*" 视同未配置处理（杜绝 allowCredentials 与通配源的组合）
     */
    private List<String> corsAllowedOrigins = List.of();
    /** 敏感字段脱敏开关 */
    private boolean sensitiveDataMasking = true;
}
