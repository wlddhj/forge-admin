package com.forge.modules.system.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.system.dto.app.AppUserDetailResponse;
import com.forge.modules.system.dto.app.AppUserQueryRequest;
import com.forge.modules.system.service.app.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "App用户管理")
@RestController
@RequestMapping("/system/app-user")
@RequiredArgsConstructor
public class AppUserAdminController {

    private final AppUserService appUserService;

    @Operation(summary = "分页查询")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:app-user:list')")
    public Result<PageResult<AppUserDetailResponse>> list(AppUserQueryRequest request) {
        IPage<AppUserDetailResponse> page = appUserService.adminPage(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:app-user:detail')")
    public Result<AppUserDetailResponse> detail(@PathVariable Long id) {
        return Result.success(appUserService.adminDetail(id));
    }

    @Operation(summary = "封禁/解封")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:app-user:update')")
    @OperationLog(title = "App用户管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        appUserService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "重置资料")
    @PutMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('system:app-user:update')")
    @OperationLog(title = "App用户管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> resetProfile(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");
        String avatar = body.get("avatar");
        appUserService.adminResetProfile(id, nickname, avatar);
        return Result.success();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:app-user:delete')")
    @OperationLog(title = "App用户管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        appUserService.deactivate(id);
        return Result.success();
    }
}