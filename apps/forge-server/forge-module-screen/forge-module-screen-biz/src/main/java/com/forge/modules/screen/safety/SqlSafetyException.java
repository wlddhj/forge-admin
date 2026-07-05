package com.forge.modules.screen.safety;

/**
 * SQL 安全校验异常
 *
 * <p>由 {@link SqlSafetyValidator} 在检测到危险 SQL 模式时抛出。
 *
 * @author standadmin
 */
public class SqlSafetyException extends RuntimeException {

    public SqlSafetyException(String message) {
        super(message);
    }
}
