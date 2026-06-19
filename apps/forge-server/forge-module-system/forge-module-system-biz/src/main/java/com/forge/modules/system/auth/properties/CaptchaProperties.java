package com.forge.modules.system.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "forge.security.captcha")
public class CaptchaProperties {
    /** 是否启用验证码（开发环境可关闭） */
    private boolean enabled = true;
    /** 验证码字符长度 */
    private int length = 4;
    /** 验证码过期秒数 */
    private int expireSeconds = 300;
    /** 验证码图片宽度 */
    private int width = 120;
    /** 验证码图片高度 */
    private int height = 40;
    /** Redis key 前缀 */
    private String redisPrefix = "captcha:";
}
