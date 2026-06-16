package com.forge.modules.system.service.sms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信配置属性
 *
 * @author forge
 */
@Data
@Component
@ConfigurationProperties(prefix = "forge.sms")
@Schema(description = "短信配置属性")
public class SmsProperties {

    /**
     * 验证码长度
     */
    @Schema(description = "验证码长度", defaultValue = "6")
    private int codeLength = 6;

    /**
     * 验证码有效期（秒）
     */
    @Schema(description = "验证码有效期（秒）", defaultValue = "300")
    private int codeTtlSeconds = 300;

    /**
     * 发送冷却时间（秒）
     */
    @Schema(description = "发送冷却时间（秒）", defaultValue = "60")
    private int sendCooldownSeconds = 60;

    /**
     * 每日发送限制
     */
    @Schema(description = "每日发送限制", defaultValue = "5")
    private int dailyLimit = 5;

    /**
     * 验证错误次数限制
     */
    @Schema(description = "验证错误次数限制", defaultValue = "5")
    private int verifyErrorLimit = 5;
}