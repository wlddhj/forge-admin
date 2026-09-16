package com.forge.modules.system.controller.admin;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.system.dto.brand.BrandRequest;
import com.forge.modules.system.dto.brand.BrandResponse;
import com.forge.modules.system.service.SysBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "品牌配置管理")
@RestController
@RequestMapping("/system/brand")
@RequiredArgsConstructor
public class SysBrandController {

    private final SysBrandService sysBrandService;

    @Operation(summary = "获取品牌配置（公共接口，登录页/布局启动时调用）")
    @GetMapping("/public")
    public Result<BrandResponse> publicBrand() {
        return Result.success(sysBrandService.getBrand());
    }

    @Operation(summary = "获取品牌配置")
    @GetMapping
    @PreAuthorize("hasAuthority('system:brand:query')")
    public Result<BrandResponse> get() {
        return Result.success(sysBrandService.getBrand());
    }

    @Operation(summary = "上传品牌Logo")
    @PostMapping("/logo")
    @PreAuthorize("hasAuthority('system:brand:update')")
    @OperationLog(title = "品牌配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        return Result.success(sysBrandService.uploadLogo(file));
    }

    @Operation(summary = "保存品牌配置")
    @PutMapping
    @PreAuthorize("hasAuthority('system:brand:update')")
    @OperationLog(title = "品牌配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody BrandRequest request) {
        sysBrandService.updateBrand(request);
        return Result.success();
    }
}
