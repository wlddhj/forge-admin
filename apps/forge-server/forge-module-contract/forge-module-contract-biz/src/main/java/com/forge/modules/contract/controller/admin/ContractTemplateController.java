package com.forge.modules.contract.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.contract.dto.TemplateDtos;
import com.forge.modules.contract.service.ContractTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同模板管理
 *
 * @author forge-admin
 */
@Tag(name = "合同模板管理")
@RestController
@RequestMapping("/contract/template")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService templateService;

    @Operation(summary = "分页查询模板")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('contract:template:list')")
    public Result<PageResult<TemplateDtos.TemplateResponse>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        Page<TemplateDtos.TemplateResponse> page =
                templateService.pageTemplates(pageNum, pageSize, name, categoryId, status);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "启用中的模板下拉（合同新建用）")
    @GetMapping("/list-enabled")
    @PreAuthorize("hasAuthority('contract:contract:create')")
    public Result<?> listEnabled() {
        return Result.success(templateService.listEnabled());
    }

    @Operation(summary = "新建模板")
    @PostMapping
    @PreAuthorize("hasAuthority('contract:template:create')")
    @OperationLog(title = "合同模板管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> create(@Valid @RequestBody TemplateDtos.CreateRequest request) {
        return Result.success(templateService.createTemplate(request));
    }

    @Operation(summary = "修改模板基础信息")
    @PutMapping
    @PreAuthorize("hasAuthority('contract:template:update')")
    @OperationLog(title = "合同模板管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody TemplateDtos.UpdateRequest request) {
        templateService.updateTemplate(request);
        return Result.success();
    }

    @Operation(summary = "仅更新纸张配置（编辑器内调整纸张/方向）")
    @PutMapping("/paper")
    @PreAuthorize("hasAuthority('contract:template:update')")
    @OperationLog(title = "合同模板管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updatePaper(@Valid @RequestBody TemplateDtos.PaperUpdateRequest request) {
        templateService.updatePaper(request);
        return Result.success();
    }

    @Operation(summary = "启用/停用模板")
    @PutMapping("/status")
    @PreAuthorize("hasAuthority('contract:template:status')")
    @OperationLog(title = "合同模板管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> changeStatus(@Valid @RequestBody TemplateDtos.StatusRequest request) {
        templateService.changeStatus(request.getId(), request.getStatus());
        return Result.success();
    }

    @Operation(summary = "模板详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:template:list')")
    public Result<TemplateDtos.TemplateResponse> getInfo(@PathVariable Long id) {
        return Result.success(templateService.getTemplate(id));
    }

    @Operation(summary = "分页查询版本历史")
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('contract:template:version')")
    public Result<PageResult<TemplateDtos.VersionResponse>> versions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        Page<TemplateDtos.VersionResponse> page = templateService.pageVersions(id, pageNum, pageSize);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "版本详情（含内容与字段定义）")
    @GetMapping("/version/{versionId}")
    @PreAuthorize("hasAuthority('contract:template:version')")
    public Result<TemplateDtos.VersionResponse> getVersion(@PathVariable Long versionId) {
        return Result.success(templateService.getVersion(versionId));
    }

    @Operation(summary = "保存新版本（编辑器保存）")
    @PostMapping("/{id}/version")
    @PreAuthorize("hasAuthority('contract:template:version')")
    @OperationLog(title = "合同模板版本", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> saveVersion(@PathVariable Long id,
                                    @Valid @RequestBody TemplateDtos.VersionSaveRequest request) {
        request.setTemplateId(id);
        return Result.success(templateService.saveVersion(request));
    }

    @Operation(summary = "修正版本字段定义（被合同引用的版本禁改）")
    @PutMapping("/version/{versionId}/field-defs")
    @PreAuthorize("hasAuthority('contract:template:version')")
    @OperationLog(title = "合同模板版本", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateFieldDefs(@PathVariable Long versionId,
                                        @Valid @RequestBody TemplateDtos.FieldDefsUpdateRequest request) {
        request.setVersionId(versionId);
        templateService.updateFieldDefs(request);
        return Result.success();
    }

    @Operation(summary = "回滚：历史版本设为当前")
    @PutMapping("/version/{versionId}/set-current")
    @PreAuthorize("hasAuthority('contract:template:version')")
    @OperationLog(title = "合同模板版本", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> setCurrentVersion(@PathVariable Long versionId) {
        templateService.setCurrentVersion(versionId);
        return Result.success();
    }

    @Operation(summary = "删除模板（有合同引用禁删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:template:delete')")
    @OperationLog(title = "合同模板管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success();
    }
}
