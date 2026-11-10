package com.forge.modules.system.controller.admin;

import com.forge.common.response.Result;
import com.forge.modules.system.auth.dto.CorpBindingRequest;
import com.forge.modules.system.auth.dto.CorpBindingResponse;
import com.forge.modules.system.auth.service.SocialAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户企业 IM（企业微信/钉钉）userid 绑定管理
 * <p>
 * 供用户管理页绑定与解绑，用于审批消息推送（workflow im.notify）的用户映射。
 * 权限复用 system:user:edit，不新增菜单。
 *
 * @author forge-admin
 */
@Tag(name = "用户企业IM绑定")
@RestController
@RequestMapping("/system/user/{userId}/corp-binding")
@RequiredArgsConstructor
public class SysUserCorpBindingController {

    private final SocialAuthService socialAuthService;

    @Operation(summary = "查询企业IM绑定")
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<CorpBindingResponse> get(@PathVariable Long userId) {
        return Result.success(socialAuthService.getCorpBinding(userId));
    }

    @Operation(summary = "全量态保存企业IM绑定（空字段=解绑）")
    @PutMapping
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<Void> save(@PathVariable Long userId, @Valid @RequestBody CorpBindingRequest request) {
        socialAuthService.saveCorpBinding(userId, request);
        return Result.success();
    }
}
