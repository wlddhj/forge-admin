package com.forge.common.config;

import com.forge.framework.security.config.AppJwtAuthenticationFilter;
import com.forge.modules.system.auth.security.JwtAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import com.forge.modules.system.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;
    private final SecurityProperties securityProperties;
    private final Environment environment;

    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          @Lazy UserDetailsService userDetailsService,
                          SecurityProperties securityProperties,
                          Environment environment) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.userDetailsService = userDetailsService;
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    private static final String[] ADMIN_WHITE_LIST = {
            "/admin-api/auth/login",
            "/admin-api/auth/register",
            "/admin-api/auth/captcha",
            "/admin-api/auth/refresh",
            "/admin-api/auth/change-password-first-login",
            "/admin-api/auth/social/authorize/**",
            "/admin-api/auth/social/callback/**",
            "/admin-api/oauth2/token",
            "/admin-api/oauth2/jwks",
            "/admin-api/oauth2/authorization-server",
            "/admin-api/.well-known/**",
            "/admin-api/userinfo",
            "/admin-api/connect/logout",
            // 多租户配置（公共接口，前端登录页/头部启动时调用）
            "/admin-api/system/tenant/public/**",
            // 品牌配置（公共接口，前端登录页/布局启动时调用）
            "/admin-api/system/brand/public",
            "/admin-api/ai/chat/message/stream",  // SSE 流式接口（权限在 Controller 中已检查）
            "/admin-api/screen/code/**",  // 大屏运行时接口（公开/授权校验在 Controller/Service 完成）
    };

    private static final String[] GLOBAL_WHITE_LIST = {
            "/ws/**",
            "/app/**",
            "/doc.html",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico",
            "/file/**",
            "/uploads/**",
            "/app-api/uploads/**",
            "/ws/info/**",
            "/topic/**",
            "/error",
            // 健康探针（K8s/负载均衡）：仅健康端点匿名可达，其余 actuator 端点默认要求认证
            "/actuator/health",
            "/actuator/health/**",
    };

    private static final String[] APP_WHITE_LIST = {
            "/app-api/auth/wx-login",
            "/app-api/auth/refresh",
            "/app-api/uploads/**",
    };

    @Bean
    @Order(0)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
                                                         @Lazy JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/admin-api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // 安全响应头配置
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(contentType -> contentType.disable())
                        .xssProtection(xss -> xss.disable())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .addHeaderWriter((request, response) -> {
                            response.setHeader("X-Content-Type-Options", "nosniff");
                            response.setHeader("X-XSS-Protection", "1; mode=block");
                            response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:;");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Security 7 AuthorizationFilter 默认过滤 ASYNC/ERROR dispatch（6.x 默认只过滤 REQUEST，行为已翻转）：
                        // SSE/Flux 端点完成时容器回发 ASYNC dispatch，此时 OncePerRequestFilter 类认证过滤器被跳过、无认证上下文，
                        // 不放行会对已完成鉴权的流式请求（如 /ai/agent/chat/stream）误报 Access Denied。
                        // dispatch 类型由容器内部设置，外部请求无法伪造，真实鉴权仍在初始 REQUEST dispatch 完成。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(ADMIN_WHITE_LIST).permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain staticResourceSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/app-api/uploads/**", "/uploads/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http,
                                                       @Lazy AppJwtAuthenticationFilter appJwtAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/app-api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // 安全响应头配置
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(contentType -> contentType.disable())
                        .xssProtection(xss -> xss.disable())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .addHeaderWriter((request, response) -> {
                            response.setHeader("X-Content-Type-Options", "nosniff");
                            response.setHeader("X-XSS-Protection", "1; mode=block");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // 同 admin 链：放行 ASYNC/ERROR dispatch，防止 SSE/异步端点完成 dispatch 时误报 Access Denied
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(APP_WHITE_LIST).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(appJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // fail-closed：未匹配到 admin/app/其他链的路径（/actuator/**、swagger、
                // 以及未来新增但漏配前缀规则的 Controller）一律要求认证，绝不默认放行
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(GLOBAL_WHITE_LIST).permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = resolveAllowedOrigins();
        if (origins.isEmpty()) {
            // 非 dev 且未配置白名单：不注册任何跨域源（同源部署不受影响，跨域一律拒绝）
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 跨域源解析：显式白名单优先；未配置/配置 * 时 dev 默认本机前端，其余环境不放行任何跨域
     * （allowCredentials=true 与通配 * 的组合由此杜绝——通配配置视同未配置处理）
     */
    private List<String> resolveAllowedOrigins() {
        List<String> configured = securityProperties.getCorsAllowedOrigins();
        boolean wildcard = configured == null || configured.isEmpty()
                || configured.stream().anyMatch("*"::equals);
        if (wildcard) {
            if (environment != null && environment.acceptsProfiles(Profiles.of("dev"))) {
                return List.of("http://localhost:3000");
            }
            log.warn("未配置 forge.security.cors-allowed-origins 且非 dev 环境：不注册任何跨域源（同源部署不受影响）");
            return List.of();
        }
        return configured;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Security 7：DaoAuthenticationProvider 构造器注入 UserDetailsService，setter 已移除
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("SCOPE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
