package com.forge.framework.mybatis.holder;

import com.forge.common.utils.CryptoUtils;
import org.springframework.stereotype.Component;

/**
 * CryptoUtils 持有者
 * 供 MyBatis EncryptTypeHandler 使用
 *
 * @author standadmin
 */
@Component
public class CryptoUtilsHolder {

    private static CryptoUtils cryptoUtils;

    /**
     * 设置 CryptoUtils 实例（由 Spring 注入）
     */
    public void setCryptoUtils(CryptoUtils cryptoUtils) {
        CryptoUtilsHolder.cryptoUtils = cryptoUtils;
    }

    /**
     * 获取 CryptoUtils 实例
     */
    public static CryptoUtils get() {
        return cryptoUtils;
    }
}