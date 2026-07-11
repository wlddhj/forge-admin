package com.forge.framework.tenant.core.web;

import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantContextWebFilter extends OncePerRequestFilter {

    private final String headerName;

    public TenantContextWebFilter(TenantProperties properties) {
        this.headerName = properties.getHeader();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(headerName);
        if (header != null && !header.isBlank()) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(header.trim()));
            } catch (NumberFormatException e) {
                // 不阻断请求，让后续 SecurityFilter 报错
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}