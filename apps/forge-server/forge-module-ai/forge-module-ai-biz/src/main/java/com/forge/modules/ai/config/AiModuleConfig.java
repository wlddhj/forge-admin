package com.forge.modules.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI模块配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiModuleConfig {

    /**
     * Python服务配置
     */
    private PythonService pythonService = new PythonService();

    /**
     * 模型配置
     */
    private Models models = new Models();

    /**
     * 是否启用AI模块
     */
    private Boolean enabled = true;

    /**
     * Python服务配置类
     */
    @Data
    public static class PythonService {
        /**
         * Python服务地址
         */
        private String baseUrl = "http://localhost:8000";

        /**
         * 连接超时时间（毫秒）
         */
        private Integer connectTimeout = 5000;

        /**
         * 读超时时间（毫秒）
         */
        private Integer readTimeout = 60000;

        /**
         * 是否启用Python服务
         */
        private Boolean enabled = true;
    }

    /**
     * 模型配置类
     */
    @Data
    public static class Models {
        /**
         * 默认模型编码
         */
        private String defaultModel = "deepseek-chat";

        /**
         * 默认温度参数
         */
        private Double defaultTemperature = 0.7;

        /**
         * 默认最大Token数
         */
        private Integer defaultMaxTokens = 4096;

        /**
         * 是否启用缓存
         */
        private Boolean cacheEnabled = true;

        /**
         * 缓存过期时间（秒）
         */
        private Integer cacheExpireSeconds = 3600;
    }
}