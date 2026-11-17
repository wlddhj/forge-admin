package com.forge.modules.contract.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import jakarta.validation.constraints.NotNull;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.framework.web.annotation.RepeatSubmit;
import com.forge.framework.web.utils.ExcelUtils;
import com.forge.modules.contract.dto.ContractDtos;
import com.forge.modules.contract.entity.CtContract;
import com.forge.modules.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;

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

import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * 合同管理
 *
 * @author forge-admin
 */
@Tag(name = "合同管理")
@RestController
@RequestMapping("/contract/contract")
@RequiredArgsConstructor
public class ContractContractController {

    private final ContractService contractService;

    @Operation(summary = "分页查询合同")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('contract:contract:list')")
    public Result<PageResult<ContractDtos.ContractResponse>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        Page<ContractDtos.ContractResponse> page =
                contractService.pageContracts(pageNum, pageSize, title, contractNo, categoryId, status);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "合同详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:contract:list')")
    public Result<ContractDtos.ContractResponse> getInfo(@PathVariable Long id) {
        return Result.success(contractService.getContract(id));
    }

    @Operation(summary = "新建合同草稿")
    @PostMapping
    @PreAuthorize("hasAuthority('contract:contract:create')")
    @OperationLog(title = "合同管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> create(@Valid @RequestBody ContractDtos.CreateRequest request) {
        return Result.success(contractService.createContract(request));
    }

    @Operation(summary = "编辑合同草稿（驳回/撤销后重新编辑）")
    @PutMapping
    @PreAuthorize("hasAuthority('contract:contract:update')")
    @OperationLog(title = "合同管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody ContractDtos.UpdateRequest request) {
        contractService.updateContract(request);
        return Result.success();
    }

    @Operation(summary = "修改备注（任意状态可改，含审批中/已定稿）")
    @PutMapping("/{id}/remark")
    @PreAuthorize("hasAuthority('contract:contract:update')")
    @OperationLog(title = "合同管理-备注", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateRemark(@PathVariable Long id,
                                     @Valid @RequestBody ContractDtos.RemarkRequest request) {
        contractService.updateRemark(id, request.getRemark());
        return Result.success();
    }

    @Operation(summary = "删除合同（仅草稿态）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contract:contract:delete')")
    @OperationLog(title = "合同管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        contractService.deleteContract(id);
        return Result.success();
    }

    @Operation(summary = "实时预览 PDF（草稿态）")
    @PostMapping(value = "/{id}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('contract:contract:preview')")
    public byte[] preview(@PathVariable Long id) {
        return contractService.previewPdf(id);
    }

    @Operation(summary = "临时预览 PDF（编辑表单：版本+当前表单值，不落库）")
    @PostMapping(value = "/preview-draft", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('contract:contract:preview')")
    public byte[] previewDraft(@Valid @RequestBody PreviewDraftRequest request) {
        return contractService.previewDraft(request.getTemplateVersionId(), request.getFieldValues());
    }

    /** 临时预览请求体 */
    @Data
    public static class PreviewDraftRequest {
        @NotNull(message = "模板版本ID不能为空")
        private Long templateVersionId;

        private Map<String, Object> fieldValues;
    }

    @Operation(summary = "打印 PDF（按所选纸张重渲染，草稿/定稿水印）")
    @PostMapping(value = "/{id}/print", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('contract:contract:print')")
    public byte[] print(@PathVariable Long id, @RequestBody(required = false) ContractDtos.PrintRequest request) {
        return contractService.printPdf(id, request == null ? new ContractDtos.PrintRequest() : request);
    }

    @Operation(summary = "提交审批")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('contract:contract:submit')")
    @RepeatSubmit
    @OperationLog(title = "合同管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> submit(@PathVariable Long id) {
        contractService.submitContract(id);
        return Result.success();
    }

    @Operation(summary = "导出合同清单")
    @PostMapping("/export")
    @PreAuthorize("hasAuthority('contract:contract:export')")
    public void export(@RequestParam(required = false) String title,
                       @RequestParam(required = false) String contractNo,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Integer status,
                       HttpServletResponse response) {
        Page<ContractDtos.ContractResponse> page = contractService.pageContracts(1, 10_000,
                title, contractNo, categoryId, status);
        List<ContractExportRow> rows = new ArrayList<>();
        for (ContractDtos.ContractResponse c : page.getRecords()) {
            ContractExportRow row = new ContractExportRow();
            row.setContractNo(c.getContractNo());
            row.setTitle(c.getTitle());
            row.setCategoryName(c.getCategoryName());
            row.setCategoryCode(c.getCategoryCode());
            row.setTemplateName(c.getTemplateName());
            row.setTemplateVersionNo(c.getTemplateVersionNo());
            row.setStatusLabel(statusLabel(c.getStatus()));
            row.setCreateBy(c.getCreateBy());
            row.setCreateTime(c.getCreateTime());
            rows.add(row);
        }
        ExcelUtils.export(response, "合同清单", "合同清单", ContractExportRow.class, rows);
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CtContract.STATUS_DRAFT -> "草稿";
            case CtContract.STATUS_APPROVING -> "审批中";
            case CtContract.STATUS_APPROVED -> "已通过";
            case CtContract.STATUS_REJECTED -> "已驳回";
            case CtContract.STATUS_CANCELED -> "已撤销";
            default -> String.valueOf(status);
        };
    }
}
