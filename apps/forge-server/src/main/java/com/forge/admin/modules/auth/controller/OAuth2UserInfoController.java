package com.forge.admin.modules.auth.controller;

import com.forge.admin.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 UserInfo 端点
 * 供通过授权服务器获取 token 的客户端调用
 */
@Tag(name = "OAuth2 UserInfo")
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class OAuth2UserInfoController {

    @Operation(summary = "获取用户信息")
    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userinfo(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Result.failed("未认证");
        }
        Map<String, Object> claims = new HashMap<>(jwt.getClaims());
        return Result.success(claims);
    }
}
