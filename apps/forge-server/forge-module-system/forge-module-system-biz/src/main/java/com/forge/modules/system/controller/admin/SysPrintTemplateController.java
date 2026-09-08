package com.forge.modules.system.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.system.dto.print.PrintTemplateQueryRequest;
import com.forge.modules.system.dto.print.PrintTemplateRequest;
import com.forge.modules.system.dto.print.PrintTemplateResponse;
import com.forge.modules.system.service.SysPrintTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 打印模板控制器
 */
@Tag(name = "打印模板管理")
@RestController
@RequestMapping("/system/print-template")
@RequiredArgsConstructor
public class SysPrintTemplateController {

    private final SysPrintTemplateService sysPrintTemplateService;

    @Operation(summary = "分页查询打印模板")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:print-template:list')")
    public Result<PageResult<PrintTemplateResponse>> list(PrintTemplateQueryRequest request) {
        Page<PrintTemplateResponse> page = sysPrintTemplateService.pagePrintTemplates(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "获取打印模板详情")
    @Parameter(name = "id", description = "模板ID", required = true)
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:print-template:query')")
    public Result<PrintTemplateResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysPrintTemplateService.getPrintTemplateDetail(id));
    }

    @Operation(summary = "按编号查询打印模板")
    @Parameter(name = "code", description = "模板编号", required = true)
    @GetMapping("/code/{code}")
    public Result<PrintTemplateResponse> getByCode(@PathVariable String code) {
        return Result.success(sysPrintTemplateService.getPrintTemplateByCode(code));
    }

    @Operation(summary = "新增打印模板")
    @PostMapping
    @PreAuthorize("hasAuthority('system:print-template:add')")
    public Result<Long> add(@Valid @RequestBody PrintTemplateRequest request) {
        return Result.success(sysPrintTemplateService.addPrintTemplate(request));
    }

    @Operation(summary = "修改打印模板")
    @PutMapping
    @PreAuthorize("hasAuthority('system:print-template:edit')")
    public Result<Void> edit(@Valid @RequestBody PrintTemplateRequest request) {
        sysPrintTemplateService.updatePrintTemplate(request);
        return Result.success();
    }

    @Operation(summary = "删除打印模板")
    @Parameter(name = "ids", description = "模板ID列表（逗号分隔）", required = true)
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:print-template:remove')")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysPrintTemplateService.deletePrintTemplates(ids);
        return Result.success();
    }
}
