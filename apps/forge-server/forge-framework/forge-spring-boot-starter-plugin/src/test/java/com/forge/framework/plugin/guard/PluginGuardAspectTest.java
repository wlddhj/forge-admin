package com.forge.framework.plugin.guard;

import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 插件请求闸切面测试：未装配放行 / 无租户上下文拒绝 / 未启用拒绝 / 启用放行。
 *
 * @author forge-admin
 */
@DisplayName("插件请求闸切面")
class PluginGuardAspectTest {

    private final ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);

    @AfterEach
    void cleanup() {
        UserContext.set(null);
    }

    @Test
    @DisplayName("未装配 Resolver（插件管理模块缺失）：放行")
    void noResolverProceeds() throws Throwable {
        ObjectProvider<PluginStateResolver> empty = mockProvider(null);
        PluginGuardAspect aspect = new PluginGuardAspect(empty);
        when(pjp.proceed()).thenReturn("ok");
        assertThat(aspect.around(pjp, guard("demo"))).isEqualTo("ok");
    }

    @Test
    @DisplayName("无租户上下文（异步线程）：fail-closed 拒绝")
    void noTenantContextRejected() throws Throwable {
        PluginGuardAspect aspect = new PluginGuardAspect(mockProvider(enabledResolver()));
        UserContext.set(null);
        assertThatThrownBy(() -> aspect.around(pjp, guard("demo")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.PLUGIN_DISABLED.getCode()))
                .hasMessageContaining("缺少租户上下文");
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("租户未启用插件：拒绝")
    void disabledRejected() throws Throwable {
        PluginStateResolver resolver = mock(PluginStateResolver.class);
        when(resolver.isEnabled(1L, "demo")).thenReturn(false);
        PluginGuardAspect aspect = new PluginGuardAspect(mockProvider(resolver));
        loginAs(1L);
        assertThatThrownBy(() -> aspect.around(pjp, guard("demo")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用或授权已失效");
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("租户已启用插件：放行")
    void enabledProceeds() throws Throwable {
        PluginGuardAspect aspect = new PluginGuardAspect(mockProvider(enabledResolver()));
        loginAs(1L);
        when(pjp.proceed()).thenReturn("ok");
        assertThat(aspect.around(pjp, guard("demo"))).isEqualTo("ok");
    }

    // ===== 工具 =====

    private PluginGuard guard(String code) {
        return DemoGuard.class.getAnnotation(PluginGuard.class).value().equals(code)
                ? DemoGuard.class.getAnnotation(PluginGuard.class)
                : OtherGuard.class.getAnnotation(PluginGuard.class);
    }

    @PluginGuard("demo")
    static class DemoGuard {
    }

    @PluginGuard("other")
    static class OtherGuard {
    }

    private PluginStateResolver enabledResolver() {
        PluginStateResolver resolver = mock(PluginStateResolver.class);
        when(resolver.isEnabled(1L, "demo")).thenReturn(true);
        return resolver;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PluginStateResolver> mockProvider(PluginStateResolver resolver) {
        ObjectProvider<PluginStateResolver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(resolver);
        return provider;
    }

    private void loginAs(Long tenantId) {
        UserContext context = new UserContext();
        context.setUserId(100L);
        context.setTenantId(tenantId);
        UserContext.set(context);
    }
}
