package com.forge.modules.screen.controller.admin;

import com.forge.common.response.Result;
import com.forge.modules.screen.safety.SqlSafetyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 大屏模块异常处理器。
 *
 * <p>放在 screen-biz 内部（而非全局 GlobalExceptionHandler），避免 forge-common
 * 反向依赖业务模块；HIGHEST_PRECEDENCE 保证先于全局兜底处理。
 *
 * @author standadmin
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScreenExceptionHandler {

    @ExceptionHandler(SqlSafetyException.class)
    public Result<Void> handleSqlSafety(SqlSafetyException e) {
        log.warn("SQL 安全拦截: {}", e.getMessage());
        return Result.failed(400, e.getMessage());
    }
}
