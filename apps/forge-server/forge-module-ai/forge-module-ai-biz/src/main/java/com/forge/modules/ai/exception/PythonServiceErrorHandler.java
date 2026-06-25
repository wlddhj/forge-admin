package com.forge.modules.ai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Python AI服务异常处理器
 * 将技术异常转换为用户友好的错误消息
 */
@Slf4j
public class PythonServiceErrorHandler {

    /**
     * 服务不可用提示
     */
    public static final String SERVICE_UNAVAILABLE_MSG = "AI服务暂时不可用，请稍后重试或联系管理员";

    /**
     * 服务未启动提示
     */
    public static final String SERVICE_NOT_STARTED_MSG = "AI服务未启动，请联系管理员启动 forge-ai-python 服务（端口 8000）";

    /**
     * 连接超时提示
     */
    public static final String CONNECTION_TIMEOUT_MSG = "AI服务连接超时，请检查网络连接或稍后重试";

    /**
     * 服务响应超时提示
     */
    public static final String RESPONSE_TIMEOUT_MSG = "AI服务响应超时，请求处理时间过长，请稍后重试";

    /**
     * 服务内部错误提示
     */
    public static final String SERVICE_INTERNAL_ERROR_MSG = "AI服务内部错误，请联系管理员处理";

    /**
     * API密钥配置错误提示
     */
    public static final String API_KEY_ERROR_MSG = "AI服务API密钥未配置或配置错误，请联系管理员检查配置";

    /**
     * 处理异常并返回用户友好的错误消息
     */
    public static String handleException(Throwable e, String operation) {
        log.error("Python AI服务调用失败 [操作: {}]: {}", operation, e.getMessage());

        // 连接被拒绝 - 服务未启动
        if (isConnectionRefused(e)) {
            return SERVICE_NOT_STARTED_MSG;
        }

        // 连接超时
        if (isConnectionTimeout(e)) {
            return CONNECTION_TIMEOUT_MSG;
        }

        // 响应超时
        if (isResponseTimeout(e)) {
            return RESPONSE_TIMEOUT_MSG;
        }

        // 服务端错误 (4xx, 5xx)
        if (isServerError(e)) {
            WebClientResponseException responseException = (WebClientResponseException) e;
            int statusCode = responseException.getStatusCode().value();

            // 401/403 - 认证/授权错误
            if (statusCode == 401 || statusCode == 403) {
                return API_KEY_ERROR_MSG;
            }

            // 500+ - 服务内部错误
            if (statusCode >= 500) {
                return SERVICE_INTERNAL_ERROR_MSG;
            }

            // 其他客户端错误
            return "请求参数错误: " + responseException.getStatusText();
        }

        // 其他未知异常
        return SERVICE_UNAVAILABLE_MSG;
    }

    /**
     * 判断是否为连接被拒绝异常（服务未启动）
     */
    public static boolean isConnectionRefused(Throwable e) {
        if (e instanceof WebClientRequestException) {
            Throwable cause = e.getCause();
            return cause instanceof ConnectException &&
                    cause.getMessage().contains("Connection refused");
        }
        return false;
    }

    /**
     * 判断是否为连接超时异常
     */
    public static boolean isConnectionTimeout(Throwable e) {
        if (e instanceof WebClientRequestException) {
            Throwable cause = e.getCause();
            return cause instanceof TimeoutException ||
                    (cause != null && cause.getMessage() != null &&
                            cause.getMessage().toLowerCase().contains("timeout"));
        }
        return false;
    }

    /**
     * 判断是否为响应超时异常
     */
    public static boolean isResponseTimeout(Throwable e) {
        if (e instanceof WebClientResponseException) {
            WebClientResponseException responseException = (WebClientResponseException) e;
            return responseException.getStatusCode().value() == 504; // Gateway Timeout
        }
        return e instanceof TimeoutException;
    }

    /**
     * 判断是否为服务端错误
     */
    public static boolean isServerError(Throwable e) {
        return e instanceof WebClientResponseException;
    }

    /**
     * 获取服务不可用状态信息（用于健康检查）
     */
    public static Map<String, Object> getUnavailableStatus(Throwable e) {
        return Map.of(
                "status", "unavailable",
                "available", false,
                "message", handleException(e, "health-check"),
                "technicalError", e.getClass().getSimpleName(),
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 获取服务可用状态信息
     */
    public static Map<String, Object> getAvailableStatus() {
        return Map.of(
                "status", "available",
                "available", true,
                "message", "服务运行正常",
                "timestamp", System.currentTimeMillis()
        );
    }
}