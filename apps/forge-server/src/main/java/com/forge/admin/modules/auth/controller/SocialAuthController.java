package com.forge.admin.modules.auth.controller;

import com.forge.admin.common.annotation.RateLimiter;
import com.forge.admin.common.response.Result;
import com.forge.admin.common.utils.UserContext;
import com.forge.admin.modules.auth.dto.LoginResponse;
import com.forge.admin.modules.auth.dto.SocialBindRequest;
import com.forge.admin.modules.auth.dto.SocialUserResponse;
import com.forge.admin.modules.auth.service.SocialAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 社交登录控制器
 */
@Slf4j
@Tag(name = "社交登录")
@RestController
@RequestMapping("/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    @Operation(summary = "社交账号授权")
    @GetMapping("/authorize/{source}")
    @RateLimiter(keyType = RateLimiter.KeyType.IP, time = 60, count = 20)
    public void authorize(@PathVariable String source, HttpServletResponse response) throws IOException {
        String authorizeUrl = socialAuthService.getAuthorizeUrl(source);
        response.sendRedirect(authorizeUrl);
    }

    @Operation(summary = "社交账号回调")
    @GetMapping("/callback/{source}")
    @RateLimiter(keyType = RateLimiter.KeyType.IP, time = 60, count = 30)
    public void callback(@PathVariable String source,
                         @RequestParam String code,
                         @RequestParam String state,
                         HttpServletRequest httpRequest,
                         HttpServletResponse httpResponse) throws IOException {
        try {
            LoginResponse loginResponse = socialAuthService.handleCallback(source, code, state, httpRequest);
            if (loginResponse != null) {
                // 已绑定，重定向前端回调页并携带 token
                String redirectUrl = String.format("/login/callback?accessToken=%s&refreshToken=%s",
                        loginResponse.getAccessToken(), loginResponse.getRefreshToken());
                httpResponse.sendRedirect(redirectUrl);
            } else {
                // 未绑定，获取临时token并重定向到登录页提示绑定
                String tempToken = socialAuthService.getUnboundTempToken(source, code, state);
                String redirectUrl = String.format("/login?error=social_not_bound&tempToken=%s&source=%s",
                        tempToken, source);
                httpResponse.sendRedirect(redirectUrl);
            }
        } catch (Exception e) {
            log.error("社交登录回调处理失败: source={}", source, e);
            httpResponse.sendRedirect("/login?error=social_login_failed&source=" + source);
        }
    }

    @Operation(summary = "绑定社交账号")
    @PostMapping("/bind")
    public Result<Void> bind(@Valid @RequestBody SocialBindRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.failed("未登录");
        }
        socialAuthService.bindSocialAccount(userId, request.getTempToken());
        return Result.success();
    }

    @Operation(summary = "解绑社交账号")
    @PostMapping("/unbind")
    public Result<Void> unbind(@RequestParam String source) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.failed("未登录");
        }
        socialAuthService.unbindSocialAccount(userId, source);
        return Result.success();
    }

    @Operation(summary = "获取已绑定的社交账号列表")
    @GetMapping("/bindings")
    public Result<List<SocialUserResponse>> bindings() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.failed("未登录");
        }
        return Result.success(socialAuthService.listBindings(userId));
    }
}
