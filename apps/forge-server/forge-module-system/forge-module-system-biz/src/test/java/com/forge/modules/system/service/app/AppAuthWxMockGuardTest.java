package com.forge.modules.system.service.app;

import com.forge.common.exception.BusinessException;
import com.forge.framework.security.config.JwtProperties;
import com.forge.modules.system.entity.AppUser;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.service.SysTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 微信登录 Mock 守卫单元测试（安全堵口第一批 T7）：
 * 生产语义 = mock-enabled 默认 false，appId 空白直接失败；禁用用户不得换新 token。
 */
@ExtendWith(MockitoExtension.class)
class AppAuthWxMockGuardTest {

    @Mock
    private AppUserService appUserService;
    @Mock
    private SysTenantService sysTenantService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;

    private AppAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-must-be-at-least-256-bits-long!!");
        jwtProperties.setExpiration(3600_000L);
        jwtProperties.setRefreshExpiration(7200_000L);

        service = new AppAuthServiceImpl(appUserService, sysTenantService, jwtProperties,
                stringRedisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "appId", "");
        ReflectionTestUtils.setField(service, "appSecret", "");
        ReflectionTestUtils.setField(service, "wxMockEnabled", false);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setStatus(1);
        lenient().when(sysTenantService.getByCode("t1")).thenReturn(tenant);
    }

    @Test
    @DisplayName("mock 关闭且 appId 未配置：登录直接失败，不建号不签发 token")
    void wxLogin_shouldFailWhenUnconfiguredWithoutMock() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.wxLogin("t1", "any-code"));
        assertEquals(true, ex.getMessage().contains("微信登录未配置"));
        verify(appUserService, never()).createAppUser(any());
    }

    @Test
    @DisplayName("禁用用户再次登录被拒，不刷新登录时间不签发 token")
    void wxLogin_shouldRejectDisabledUser() {
        ReflectionTestUtils.setField(service, "wxMockEnabled", true);
        AppUser disabled = AppUser.builder().tenantId(1L).openId("mock-x").status(0).build();
        disabled.setId(9L);
        when(appUserService.getByTenantIdAndOpenId(eq(1L), anyString())).thenReturn(disabled);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.wxLogin("t1", "1"));
        assertEquals(true, ex.getMessage().contains("禁用"));
        verify(appUserService, never()).updateLastLoginTime(anyLong());
    }

    @Test
    @DisplayName("mock 开启（dev）：appId 未配置仍可走模拟 openId 正常登录")
    void wxLogin_shouldAllowMockWhenExplicitlyEnabled() {
        ReflectionTestUtils.setField(service, "wxMockEnabled", true);
        when(appUserService.getByTenantIdAndOpenId(eq(1L), anyString())).thenReturn(null);
        doAnswer(invocation -> {
            invocation.getArgument(0, AppUser.class).setId(9L);
            return null;
        }).when(appUserService).createAppUser(any());

        var response = service.wxLogin("t1", "code-1");
        assertNotNull(response.getAccessToken(), "dev Mock 模式应能完成登录签发");
        verify(appUserService).createAppUser(any());
    }
}
