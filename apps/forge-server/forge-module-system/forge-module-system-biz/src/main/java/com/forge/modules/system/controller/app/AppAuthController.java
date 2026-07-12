package com.forge.modules.system.controller.app;

import com.forge.common.response.Result;
import com.forge.modules.system.dto.app.AppLoginResponse;
import com.forge.modules.system.dto.app.WxLoginRequest;
import com.forge.modules.system.service.app.AppAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "移动端 - 认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AppAuthController {

    private final AppAuthService appAuthService;

    @Operation(summary = "微信登录")
    @PostMapping("/wx-login")
    public Result<AppLoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        return Result.success(appAuthService.wxLogin(request.getTenantCode(), request.getCode()));
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<AppLoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.failed("refreshToken不能为空");
        }
        return Result.success(appAuthService.refreshToken(refreshToken));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String accessToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;
        String refreshToken = request.getHeader("X-Refresh-Token");
        appAuthService.logout(accessToken, refreshToken);
        return Result.success();
    }
}
