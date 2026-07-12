package com.forge.framework.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.forge.framework.tenant.core.aop.TenantIgnoreAspect;
import com.forge.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.forge.framework.tenant.core.job.TenantJobAspect;
import com.forge.framework.tenant.core.service.TenantFrameworkService;
import com.forge.framework.tenant.core.service.TenantFrameworkServiceImpl;
import com.forge.framework.tenant.core.web.TenantContextWebFilter;
import com.forge.framework.tenant.core.web.TenantSecurityWebFilter;
import com.forge.modules.system.api.tenant.TenantApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "forge.tenant", value = "enable", havingValue = "true", matchIfMissing = true)
public class TenantAutoConfiguration {

    @Bean
    public TenantFrameworkService tenantFrameworkService(TenantApi tenantApi) {
        return new TenantFrameworkServiceImpl(tenantApi);
    }

    @Bean
    public TenantIgnoreAspect tenantIgnoreAspect() {
        return new TenantIgnoreAspect();
    }

    @Bean
    public TenantJobAspect tenantJobAspect() {
        return new TenantJobAspect();
    }

    @Bean
    public TenantDatabaseInterceptor tenantDatabaseInterceptor(TenantProperties properties) {
        return new TenantDatabaseInterceptor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantDatabaseInterceptor handler,
                                                                  MybatisPlusInterceptor interceptor) {
        TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(handler);
        interceptor.addInnerInterceptor(inner);
        return inner;
    }

    @Bean
    public FilterRegistrationBean<TenantContextWebFilter> tenantContextWebFilter(TenantProperties properties) {
        FilterRegistrationBean<TenantContextWebFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TenantContextWebFilter(properties));
        bean.setOrder(0);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TenantSecurityWebFilter> tenantSecurityWebFilter(
            TenantProperties properties, TenantFrameworkService service) {
        FilterRegistrationBean<TenantSecurityWebFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TenantSecurityWebFilter(properties, service));
        bean.setOrder(1);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
