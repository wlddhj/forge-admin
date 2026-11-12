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
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

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
        // MP 官方要求 TenantLine 插件必须先于分页/数据权限插件注册（先做租户改写再 count/分页），
        // 而 MybatisPlusConfig 已按 DataPermission→Pagination→OptimisticLocker 建链；
        // addInnerInterceptor 只能追加尾部且 getInterceptors() 返回不可变视图，
        // 故重建链表把 TenantLine 置于链首后整体写回
        List<InnerInterceptor> chain = new ArrayList<>(interceptor.getInterceptors());
        chain.add(0, inner);
        interceptor.setInterceptors(chain);
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
