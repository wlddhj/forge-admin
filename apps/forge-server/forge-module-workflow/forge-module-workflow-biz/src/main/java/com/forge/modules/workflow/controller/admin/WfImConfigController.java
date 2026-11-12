package com.forge.modules.workflow.controller.admin;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.workflow.dto.im.ImChannelConfigRequest;
import com.forge.modules.workflow.dto.im.ImChannelConfigResponse;
import com.forge.modules.workflow.im.ImChannelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IM 渠道配置管理（按租户维度）
 * <p>
 * 每租户每渠道一行（wework/dingtalk），secret 加密入库且出参不回显；
 * PUT 语义为 upsert，secret 留空 = 保留原值。
 *
 * @author forge-admin
 */
@Tag(name = "IM通知配置")
@RestController
@RequestMapping("/workflow/im-config")
@RequiredArgsConstructor
@Validated
public class WfImConfigController {

    private final ImChannelConfigService configService;

    @Operation(summary = "按租户查询渠道配置")
    @GetMapping
    @PreAuthorize("hasAuthority('workflow:im:config:list')")
    public Result<List<ImChannelConfigResponse>> list(@RequestParam @NotNull Long tenantId) {
        return Result.success(configService.listByTenant(tenantId));
    }

    @Operation(summary = "保存渠道配置（upsert，secret 留空=保留原值）")
    @PutMapping
    @PreAuthorize("hasAuthority('workflow:im:config:edit')")
    @OperationLog(title = "IM通知配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> save(@Valid @RequestBody ImChannelConfigRequest request) {
        configService.upsert(request);
        return Result.success();
    }

    @Operation(summary = "删除渠道配置")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:im:config:delete')")
    @OperationLog(title = "IM通知配置", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        configService.deleteConfig(id);
        return Result.success();
    }
}
