package com.forge.framework.tenant.core.web;

import com.forge.framework.tenant.config.TenantProperties;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.framework.tenant.core.service.TenantFrameworkService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantSecurityWebFilterTest {

    private TenantSecurityWebFilter filter;
    private TenantFrameworkService frameworkService;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setIgnoreUrls(Set.of("/admin-api/auth/login"));
        frameworkService = mock(TenantFrameworkService.class);
        filter = new TenantSecurityWebFilter(props, frameworkService);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void ignoreUrl_withoutTenantId_callsChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin-api/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // Capture the ignore state INSIDE the filter's execution (before finally clears it)
        AtomicBoolean ignoreDuringFilter = new AtomicBoolean(false);
        FilterChain capturingChain = (r, response) -> {
            ignoreDuringFilter.set(TenantContextHolder.isIgnore());
            chain.doFilter(r, response);
        };

        filter.doFilter(req, res, capturingChain);
        verify(chain).doFilter(req, res);
        assertTrue(ignoreDuringFilter.get(), "isIgnore should be true during filter execution");
    }

    @Test
    void normalUrl_withoutTenantId_writes400() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin-api/system/user/list");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(chain, never()).doFilter(req, res);
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("租户标识未传递"));
    }

    @Test
    void normalUrl_withValidTenant_callsValidTenant() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/admin-api/system/user/list");
        req.addHeader("X-Tenant-Id", "1");
        TenantContextHolder.setTenantId(1L);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        verify(frameworkService).validTenant(1L);
        verify(chain).doFilter(req, res);
    }
}