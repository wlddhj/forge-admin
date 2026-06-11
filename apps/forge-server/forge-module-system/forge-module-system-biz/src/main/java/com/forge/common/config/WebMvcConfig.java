package com.forge.common.config;

import com.forge.framework.web.config.WebProperties;
import com.forge.modules.system.entity.SysFileConfig;
import com.forge.modules.system.service.SysFileConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SysFileConfigService sysFileConfigService;
    private final WebProperties webProperties;

    /**
     * 配置路径匹配，自动为Controller添加路径前缀
     * - admin-api前缀：用于 **.controller.admin.** 包下的RestController
     * - app-api前缀：用于 **.controller.app.** 包下的RestController
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

    /**
     * 配置静态资源处理
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 从文件配置中获取默认本地存储路径
        SysFileConfig config = sysFileConfigService.getDefaultConfig();
        String uploadPath = "./uploads";
        if (config != null && "local".equals(config.getStorageType()) && config.getBasePath() != null) {
            uploadPath = config.getBasePath();
        }

        String absolutePath = new java.io.File(uploadPath).getAbsolutePath();

        // 静态资源映射（移除 /api/uploads/** 因为context-path将被移除）
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
