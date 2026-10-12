package com.forge.common.config;

import com.forge.framework.web.config.WebProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final WebProperties webProperties;

    /**
     * 配置路径匹配，自动为Controller添加路径前缀
     * - admin-api前缀：用于 **.controller.admin.** 包下的RestController
     * - app-api前缀：用于 **.controller.app.** 包下的RestController
     *
     * 注：/uploads/** 与 /app-api/uploads/** 由 UploadsController 按请求动态解析，
     * 存储路径变更后无需重启
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        AntPathMatcher antPathMatcher = new AntPathMatcher(".");
        configurer.addPathPrefix(webProperties.getAdminApi().getPrefix(), clazz ->
                clazz.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)
                && antPathMatcher.match(webProperties.getAdminApi().getController(), clazz.getPackage().getName()));
        configurer.addPathPrefix(webProperties.getAppApi().getPrefix(), clazz ->
                clazz.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)
                && antPathMatcher.match(webProperties.getAppApi().getController(), clazz.getPackage().getName()));
    }
}
