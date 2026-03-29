package com.forge.admin.modules.system.controller;

import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.online.OnlineUserResponse;
import com.forge.admin.modules.system.service.SysOnlineUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 在线用户控制器
 */
@Tag(name = "在线用户管理")
@RestController
@RequestMapping("/system/online-user")
@RequiredArgsConstructor
public class SysOnlineUserController {

    private final SysOnlineUserService sysOnlineUserService;

    @Operation(summary = "获取在线用户列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:online-user:list')")
    public Result<List<OnlineUserResponse>> list() {
        return Result.success(sysOnlineUserService.getOnlineUsers());
    }

    @Operation(summary = "强制下线")
    @DeleteMapping("/{tokenId}")
    @PreAuthorize("hasAuthority('system:online-user:force-logout')")
    public Result<Void> forceLogout(@PathVariable String tokenId) {
        sysOnlineUserService.forceLogout(tokenId);
        return Result.success();
    }
}
