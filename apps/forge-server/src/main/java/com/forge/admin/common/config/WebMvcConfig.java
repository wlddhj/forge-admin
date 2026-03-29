package com.forge.admin.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @Value("${file.base-url}")
    private String baseUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射上传文件目录（使用绝对路径确保兼容性）
        String absolutePath = new java.io.File(uploadPath).getAbsolutePath();

        // 由于 context-path 是 /api，静态资源需要注册在根路径下
        // 但由于 Spring Boot 的限制，我们需要特殊处理
        // 方案：将静态资源映射到 /api/uploads/** 下，并调整 base-url

        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");

        // 同时也注册 /uploads/** 以支持直接访问（如果在根路径下）
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
