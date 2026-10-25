package com.forge.framework.web.config;

import com.forge.framework.web.filter.XssFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * XSS 过滤器自动配置
 *
 * @author standadmin
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "forge.security", name = "xss-enabled", havingValue = "true", matchIfMissing = true)
public class XssFilterAutoConfiguration {

    @Bean
    // actuator 引入后容器存在两个 HandlerMapping(WebMvc 主映射 + controllerEndpoint 映射),显式指定主映射
    public FilterRegistrationBean<XssFilter> xssFilterRegistration(
            @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping handlerMapping) {
        XssFilter xssFilter = new XssFilter();
        xssFilter.setHandlerMapping(handlerMapping);

        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(xssFilter);
        registration.addUrlPatterns("/*");
        registration.setName("xssFilter");
        registration.setOrder(1);  // 高优先级
        return registration;
    }
}