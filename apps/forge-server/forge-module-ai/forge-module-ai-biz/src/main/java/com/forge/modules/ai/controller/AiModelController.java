package com.forge.modules.ai.controller;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.response.ModelListResponse;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.service.AiModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI模型配置控制器
 */
@Slf4j
@Tag(name = "AI模型配置")
@RestController
@RequestMapping("/ai/model")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    @Operation(summary = "获取可用模型列表")
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('ai:model:query')")
    public Result<ModelListResponse> getAvailableModels() {
        ModelListResponse response = aiModelService.getAvailableModels();
        return Result.success(response);
    }

    @Operation(summary = "获取所有模型配置")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ai:model:query')")
    public Result<List<AiModelConfig>> getAllModelConfigs() {
        List<AiModelConfig> list = aiModelService.getAllModelConfigs();
        return Result.success(list);
    }

    @Operation(summary = "获取模型配置详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:model:query')")
    public Result<AiModelConfig> getModelConfig(@PathVariable Long id) {
        AiModelConfig config = aiModelService.getModelConfig(id);
        return Result.success(config);
    }

    @Operation(summary = "更新模型配置状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ai:model:config')")
    @OperationLog(title = "AI模型配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateModelStatus(@PathVariable Long id, @RequestParam Integer status) {
        aiModelService.updateModelStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "切换默认模型")
    @PutMapping("/{id}/default")
    @PreAuthorize("hasAuthority('ai:model:switch')")
    @OperationLog(title = "AI模型配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> switchDefaultModel(@PathVariable Long id) {
        // 设置为默认模型（status=1且is_default=1）
        aiModelService.updateModelStatus(id, 1);
        return Result.success();
    }

    @Operation(summary = "刷新模型缓存")
    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('ai:model:config')")
    @OperationLog(title = "AI模型配置", businessType = OperationLog.BusinessType.OTHER)
    public Result<Void> refreshModelCache() {
        aiModelService.refreshModelCache();
        return Result.success();
    }
}