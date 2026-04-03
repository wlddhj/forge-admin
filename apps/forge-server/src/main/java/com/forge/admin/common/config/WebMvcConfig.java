package com.forge.admin.common.config;

import com.forge.admin.modules.system.entity.SysFileConfig;
import com.forge.admin.modules.system.service.SysFileConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SysFileConfigService sysFileConfigService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 从文件配置中获取默认本地存储路径
        SysFileConfig config = sysFileConfigService.getDefaultConfig();
        String uploadPath = "./uploads";
        if (config != null && "local".equals(config.getStorageType()) && config.getBasePath() != null) {
            uploadPath = config.getBasePath();
        }

        String absolutePath = new java.io.File(uploadPath).getAbsolutePath();

        // 由于 context-path 是 /api，静态资源需要注册在对应路径下
        registry.addResourceHandler("/api/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");

        // 同时也注册 /uploads/** 以支持直接访问
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
