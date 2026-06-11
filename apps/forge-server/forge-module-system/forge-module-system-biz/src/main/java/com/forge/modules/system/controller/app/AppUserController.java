package com.forge.modules.system.controller.app;

import com.forge.common.response.Result;
import com.forge.modules.system.dto.app.AppUserProfileResponse;
import com.forge.modules.system.dto.app.AppUserProfileUpdateRequest;
import com.forge.modules.system.service.app.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "移动端 - 用户")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService appUserService;

    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    public Result<AppUserProfileResponse> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("appUserId");
        if (userId == null) {
            return Result.failed("未登录");
        }
        return Result.success(appUserService.getProfile(userId));
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(HttpServletRequest request,
                                       @Valid @RequestBody AppUserProfileUpdateRequest updateRequest) {
        Long userId = (Long) request.getAttribute("appUserId");
        if (userId == null) {
            return Result.failed("未登录");
        }
        appUserService.updateProfile(userId, updateRequest);
        return Result.success();
    }
}
