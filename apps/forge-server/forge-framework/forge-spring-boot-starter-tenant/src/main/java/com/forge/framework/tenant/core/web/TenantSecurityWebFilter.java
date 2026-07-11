package com.forge.framework.tenant.core.web;

import cn.hutool.core.collection.CollUtil;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.Result;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.UserContext;
import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.framework.tenant.core.service.TenantFrameworkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class TenantSecurityWebFilter extends OncePerRequestFilter {

    private final TenantProperties tenantProperties;
    private final TenantFrameworkService tenantFrameworkService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantSecurityWebFilter(TenantProperties tenantProperties,
                                   TenantFrameworkService tenantFrameworkService) {
        this.tenantProperties = tenantProperties;
        this.tenantFrameworkService = tenantFrameworkService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long tenantId = TenantContextHolder.getTenantId();
        UserContext user = UserContext.get();

        // (a) 登录用户：校验越权（平台管理员不校验）
        boolean isPlatformAdmin = user != null && user.getAccountType() != null && user.getAccountType() == 2;
        if (user != null && !isPlatformAdmin) {
            if (tenantId == null) {
                tenantId = user.getDeptId() == null ? null : extractTenantIdFromUser(user);
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                }
            } else if (!Objects.equals(extractTenantIdFromUser(user), tenantId)) {
                log.error("[tenant] 用户({}) 越权访问租户({}) URL({})",
                        user.getUserId(), tenantId, request.getRequestURI());
                writeError(response, ResultCode.FORBIDDEN.getCode(), "您无权访问该租户的数据");
                return;
            }
        }

        // (b) 非忽略 URL 必须有 tenantId
        boolean ignoreUrl = isIgnoreUrl(request);
        if (!ignoreUrl) {
            if (tenantId == null) {
                if (!isPlatformAdmin) {
                    log.error("[tenant] URL({}) 未传递租户编号", request.getRequestURI());
                    writeError(response, ResultCode.VALIDATE_FAILED.getCode(), "请求的租户标识未传递，请检查请求头 X-Tenant-Id");
                    return;
                }
            } else {
                try {
                    tenantFrameworkService.validTenant(tenantId);
                } catch (BusinessException e) {
                    writeError(response, ResultCode.FORBIDDEN.getCode(), e.getMessage());
                    return;
                }
            }
        } else {
            if (tenantId == null) {
                TenantContextHolder.setIgnore(true);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Long extractTenantIdFromUser(UserContext user) {
        // LoginUser 需要新增 tenantId 字段；此处先用 placeholder
        // 实际实现依赖 UserContext 扩展
        return null;
    }

    private boolean isIgnoreUrl(HttpServletRequest request) {
        Set<String> ignoreUrls = tenantProperties.getIgnoreUrls();
        if (CollUtil.isEmpty(ignoreUrls)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (ignoreUrls.contains(uri)) {
            return true;
        }
        for (String pattern : ignoreUrls) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.failed(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}