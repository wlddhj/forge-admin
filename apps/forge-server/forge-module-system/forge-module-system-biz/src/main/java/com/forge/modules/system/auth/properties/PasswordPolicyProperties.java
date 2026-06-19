package com.forge.modules.system.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "forge.security.password")
public class PasswordPolicyProperties {
    /** 密码最小长度 */
    private int minLength = 8;
    /** 密码最大长度 */
    private int maxLength = 32;
    /** 是否要求包含大写字母 */
    private boolean requireUppercase = true;
    /** 是否要求包含小写字母 */
    private boolean requireLowercase = true;
    /** 是否要求包含数字 */
    private boolean requireDigit = true;
    /** 是否要求包含特殊字符 */
    private boolean requireSpecial = true;
    /** 允许的特殊字符 */
    private String specialChars = "!@#$%^&*()-_=+[]{}|;:,.<>?";
    /** 密码历史保留条数（N 次不复用） */
    private int historySize = 5;
    /** 密码有效期天数（0 表示不校验） */
    private int expireDays = 90;
    /** BCrypt strength（10-14） */
    private int bcryptStrength = 12;
    /** AES 加密密钥（来自环境变量 APP_AES_KEY，16/24/32 字节） */
    private String aesKey;
    /** 随机默认密码长度 */
    private int randomPasswordLength = 12;
}
