package com.forge.modules.system.controller.app;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.RepeatSubmit;
import com.forge.modules.system.annotation.AssertAppUserActive;
import com.forge.modules.system.dto.app.AppUserProfileResponse;
import com.forge.modules.system.dto.app.AppUserProfileUpdateRequest;
import com.forge.modules.system.dto.app.DeactivateRequest;
import com.forge.modules.system.dto.app.PhoneBindRequest;
import com.forge.modules.system.dto.app.SmsCodeRequest;
import com.forge.modules.system.service.app.AppUserService;
import com.forge.modules.system.service.sms.SmsCodeManager;
import com.forge.modules.system.service.sms.SmsProperties;
import com.forge.modules.system.service.sms.SmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "移动端 - 用户")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;
    private final SmsCodeManager smsCodeManager;
    private final SmsService smsService;
    private final SmsProperties smsProperties;

    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    @AssertAppUserActive
    public Result<AppUserProfileResponse> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("appUserId");
        if (userId == null) {
            return Result.failed("未登录");
        }
        return Result.success(appUserService.getProfile(userId));
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    @AssertAppUserActive
    @RepeatSubmit
    public Result<Void> updateProfile(HttpServletRequest request,
                                       @Valid @RequestBody AppUserProfileUpdateRequest updateRequest) {
        Long userId = (Long) request.getAttribute("appUserId");
        if (userId == null) {
            return Result.failed("未登录");
        }
        appUserService.updateProfile(userId, updateRequest);
        return Result.success();
    }

    @Operation(summary = "发送验证码")
    @PostMapping("/sms-code")
    @AssertAppUserActive
    public Result<Map<String, Integer>> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        String code = smsCodeManager.sendCode(request.getPhone());
        smsService.send(request.getPhone(), code);
        return Result.success(Map.of("expireSeconds", smsProperties.getCodeTtlSeconds()));
    }

    @Operation(summary = "绑定/换绑手机号")
    @PostMapping("/bind-phone")
    @AssertAppUserActive
    @RepeatSubmit
    public Result<Void> bindPhone(HttpServletRequest httpRequest,
                                   @Valid @RequestBody PhoneBindRequest body) {
        Long userId = (Long) httpRequest.getAttribute("appUserId");
        smsCodeManager.verifyCode(body.getPhone(), body.getCode());
        appUserService.bindPhone(userId, body.getPhone());
        return Result.success();
    }

    @Operation(summary = "注销账号")
    @DeleteMapping("/deactivate")
    @AssertAppUserActive
    @RepeatSubmit
    public Result<Void> deactivate(HttpServletRequest httpRequest,
                                    @Valid @RequestBody DeactivateRequest body) {
        Long userId = (Long) httpRequest.getAttribute("appUserId");
        appUserService.deactivate(userId);
        return Result.success();
    }
}
