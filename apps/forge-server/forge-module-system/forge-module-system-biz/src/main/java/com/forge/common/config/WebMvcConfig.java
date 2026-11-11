package com.forge.common.config;

import com.forge.framework.web.config.WebProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
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
     * MVC 异步执行器：SSE/Flux 流式端点的异步 dispatch 专用。
     * 不配置时 Spring 退化到 SimpleAsyncTaskExecutor（每请求新建线程、无上限，生产不适合）。
     * 注意：Spring Framework 7 的 MVC 异步执行器只经 configureAsyncSupport 注入，
     * AsyncConfigurer 仅服务 @Async 注解，对 MVC 异步无效。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

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
