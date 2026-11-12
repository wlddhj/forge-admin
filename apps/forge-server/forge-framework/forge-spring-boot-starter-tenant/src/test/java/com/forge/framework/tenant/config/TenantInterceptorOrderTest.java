package com.forge.framework.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.forge.framework.tenant.core.db.TenantDatabaseInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户插件注册顺序单元测试（租户开闸前置 T8）：
 * TenantLine 必须位于分页/数据权限之前（MP 官方要求，先租户改写再 count/分页）。
 */
class TenantInterceptorOrderTest {

    @Test
    @DisplayName("TenantLine 插入链首，先于其余业务插件")
    void tenantLineShouldBeRegisteredFirst() {
        // 复刻 MybatisPlusConfig 的建链形态：业务插件先入链（DataPermission→Pagination→...）
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());

        TenantAutoConfiguration configuration = new TenantAutoConfiguration();
        TenantLineInnerInterceptor tenantLine = configuration.tenantLineInnerInterceptor(
                new TenantDatabaseInterceptor(new TenantProperties()), interceptor);

        assertEquals(3, interceptor.getInterceptors().size());
        assertEquals(tenantLine, interceptor.getInterceptors().get(0),
                "TenantLine 必须在链首（先于分页/数据权限做租户改写）");
        assertTrue(interceptor.getInterceptors().get(1) instanceof OptimisticLockerInnerInterceptor);
        assertTrue(interceptor.getInterceptors().get(2) instanceof PaginationInnerInterceptor);
    }
}
