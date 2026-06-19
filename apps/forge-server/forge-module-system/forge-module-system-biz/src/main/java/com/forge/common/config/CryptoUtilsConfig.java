package com.forge.common.config;

import com.forge.common.utils.CryptoUtils;
import com.forge.modules.system.auth.properties.PasswordPolicyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CryptoUtils Bean 配置
 * 从 PasswordPolicyProperties 读取 AES 密钥创建 CryptoUtils
 *
 * @author standadmin
 */
@Configuration
public class CryptoUtilsConfig {

    @Bean
    public CryptoUtils cryptoUtils(PasswordPolicyProperties passwordPolicyProperties) {
        return new CryptoUtils(passwordPolicyProperties.getAesKey());
    }
}