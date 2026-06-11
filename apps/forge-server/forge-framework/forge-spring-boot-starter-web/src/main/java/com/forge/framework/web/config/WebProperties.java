package com.forge.framework.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "forge.web")
public class WebProperties {

    private Api adminApi = new Api("/admin-api", "**.controller.admin.**");
    private Api appApi = new Api("/app-api", "**.controller.app.**");

    @Data
    public static class Api {
        private String prefix;
        private String controller;

        public Api() {}

        public Api(String prefix, String controller) {
            this.prefix = prefix;
            this.controller = controller;
        }
    }
}
