package com.forge.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.workflow.dto.copy.CopyQueryRequest;
import com.forge.modules.workflow.dto.copy.CopyResponse;
import com.forge.modules.workflow.service.WfProcessInstanceCopyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "流程抄送")
@RestController
@RequestMapping("/workflow/copy")
@RequiredArgsConstructor
public class WfProcessInstanceCopyController {

    private final WfProcessInstanceCopyService copyService;

    @Operation(summary = "分页查询抄送列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:task:list')")
    public Result<PageResult<CopyResponse>> list(CopyQueryRequest request) {
        Page<CopyResponse> page = copyService.pageCopy(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }
}
