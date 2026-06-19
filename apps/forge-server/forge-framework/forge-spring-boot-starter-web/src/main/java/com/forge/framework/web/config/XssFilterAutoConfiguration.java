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
    public FilterRegistrationBean<XssFilter> xssFilterRegistration(RequestMappingHandlerMapping handlerMapping) {
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