package com.forge.common.config;

import com.forge.framework.security.config.AppJwtAuthenticationFilter;
import com.forge.modules.system.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

/**
 * 默认 SecurityFilterChain fail-closed 回归测试（安全堵口第一批 T2）：
 * 未匹配 admin/app 链的路径（/actuator/**、swagger、漏配前缀的未来端点）不得默认放行；
 * 仅健康探针与显式白名单匿名可达。
 */
@SpringBootTest(classes = SecurityConfigDefaultChainTest.TestApp.class, properties = {
        // 满足 mybatis starter @MapperScan 占位符：指向无 Mapper 的包，让扫描空转（本测试不触库）
        "forge.info.base-package=com.forge.common.config"
})
@AutoConfigureMockMvc
class SecurityConfigDefaultChainTest {

    /**
     * 最小上下文：不组件扫描（避开同包 AuthorizationServerConfig/DataPermissionCacheInitializer 等重依赖），
     * 仅显式导入被测 SecurityConfig + Boot 自动配置。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
            org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
    })
    @Import(SecurityConfig.class)
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;
    /** 容器真实 Environment（本测试上下文无 profile，即非 dev） */
    @Autowired
    private org.springframework.core.env.Environment environment;

    @MockitoBean
    private com.forge.modules.system.auth.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private AppJwtAuthenticationFilter appJwtAuthenticationFilter;
    /** MybatisPlusConfig#cryptoUtilsHolder 需要；本测试不触加密字段 */
    @MockitoBean
    private com.forge.common.utils.CryptoUtils cryptoUtils;
    /** TenantAutoConfiguration#tenantFrameworkService 需要 */
    @MockitoBean
    private com.forge.modules.system.api.tenant.TenantApi tenantApi;
    /** SecurityConfig CORS 白名单来源（默认空 → 非 dev 不注册跨域源）；Environment 用容器真实实例（无 profile=非 dev） */
    @MockitoBean
    private SecurityProperties securityProperties;

    @BeforeEach
    void stubSecurityInfrastructure() throws Exception {
        // 默认链无认证过滤器，匿名访问受保护路径时经 entryPoint 拒绝；mock 需显式写 401
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            try {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (java.io.IOException ignored) {
                // 测试桩内忽略
            }
            return null;
        }).when(jwtAuthenticationEntryPoint).commence(any(), any(), any());
        // 认证过滤器桩必须放行过滤链（Mockito 默认 no-op 会截断链，请求变 200 空响应）
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        doAnswer(invocation -> {
            jakarta.servlet.FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(appJwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("actuator/prometheus 匿名访问被默认链拒绝（401）")
    void actuatorPrometheus_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("actuator 普通端点匿名访问被拒（401）")
    void actuatorEnv_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("健康探针匿名可达（白名单放行，actuator health 端点正常响应 200）")
    void actuatorHealth_shouldStayPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("健康探针子路径（liveness/readiness 探针形态）同样放行")
    void actuatorHealthLiveness_shouldStayPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("admin 链未知业务端点匿名被拒（401），不因默认链放行而暴露")
    void adminApiUnknownPath_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/admin-api/system/user/list")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS 白名单生效：显式配置的源放行（含凭据），不产生通配源")
    void cors_shouldHonorConfiguredOrigin() {
        SecurityProperties props = new SecurityProperties();
        props.setCorsAllowedOrigins(List.of("https://front.example.com"));
        SecurityConfig config = new SecurityConfig(
                org.mockito.Mockito.mock(com.forge.modules.system.auth.security.JwtAuthenticationEntryPoint.class),
                org.mockito.Mockito.mock(UserDetailsService.class),
                props, environment);
        org.springframework.web.cors.CorsConfigurationSource source = config.corsConfigurationSource();
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/admin-api/x");
        request.addHeader("Origin", "https://front.example.com");
        org.springframework.web.cors.CorsConfiguration cors = source.getCorsConfiguration(request);
        org.junit.jupiter.api.Assertions.assertNotNull(cors, "配置白名单后应返回 CORS 配置");
        org.junit.jupiter.api.Assertions.assertTrue(cors.getAllowedOriginPatterns().contains("https://front.example.com"));
        org.junit.jupiter.api.Assertions.assertFalse(cors.getAllowedOriginPatterns().contains("*"));
    }

    @Test
    @DisplayName("CORS 未配置且非 dev：不注册任何跨域源；dev 回退本机前端源")
    void cors_shouldFallbackOnlyInDev() {
        SecurityProperties unconfigured = new SecurityProperties();
        SecurityConfig config = new SecurityConfig(
                org.mockito.Mockito.mock(com.forge.modules.system.auth.security.JwtAuthenticationEntryPoint.class),
                org.mockito.Mockito.mock(UserDetailsService.class),
                unconfigured, environment);
        // 本测试上下文无 profile（非 dev）：未注册任何 URL 的跨域配置
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/admin-api/x");
        org.junit.jupiter.api.Assertions.assertNull(config.corsConfigurationSource().getCorsConfiguration(request),
                "未配置且非 dev：不注册任何跨域源");

        // dev profile：回退本机前端（源白名单只含 localhost:3000，Origin 匹配在执行阶段判定）
        org.springframework.core.env.Environment devEnv =
                org.mockito.Mockito.mock(org.springframework.core.env.Environment.class);
        org.mockito.Mockito.when(devEnv.acceptsProfiles(org.springframework.core.env.Profiles.of("dev")))
                .thenReturn(true);
        SecurityConfig devConfig = new SecurityConfig(
                org.mockito.Mockito.mock(com.forge.modules.system.auth.security.JwtAuthenticationEntryPoint.class),
                org.mockito.Mockito.mock(UserDetailsService.class),
                unconfigured, devEnv);
        org.springframework.web.cors.CorsConfiguration devCors =
                devConfig.corsConfigurationSource().getCorsConfiguration(request);
        org.junit.jupiter.api.Assertions.assertNotNull(devCors, "dev 应回退注册本机前端源");
        org.junit.jupiter.api.Assertions.assertTrue(devCors.getAllowedOriginPatterns().contains("http://localhost:3000"));
        org.junit.jupiter.api.Assertions.assertFalse(devCors.getAllowedOriginPatterns().contains("https://evil.example.com"));
    }

    @Test
    @DisplayName("未配置 CORS（非 dev）：任意源的预检请求不返回放行头（不放行任何跨域）")
    void cors_shouldRejectAllOriginsWhenUnconfigured() throws Exception {
        mockMvc.perform(options("/admin-api/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
