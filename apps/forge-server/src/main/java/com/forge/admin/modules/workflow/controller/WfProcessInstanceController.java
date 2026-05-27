package com.forge.admin.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.admin.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.admin.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.admin.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.admin.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.admin.modules.workflow.service.WfProcessInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 流程实例管理控制器
 *
 * @author forge-admin
 */
@Tag(name = "流程实例管理")
@RestController
@RequestMapping("/workflow/instance")
@RequiredArgsConstructor
public class WfProcessInstanceController {

    private final WfProcessInstanceService wfProcessInstanceService;

    @Operation(summary = "分页查询流程实例")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:instance:list')")
    public Result<PageResult<ProcessInstanceResponse>> list(ProcessInstanceQueryRequest request) {
        Page<ProcessInstanceResponse> page = wfProcessInstanceService.pageInstance(request);
        PageResult<ProcessInstanceResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "查询我的流程实例")
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('workflow:instance:list')")
    public Result<PageResult<ProcessInstanceResponse>> myInstances(ProcessInstanceQueryRequest request) {
        Page<ProcessInstanceResponse> page = wfProcessInstanceService.getMyInstances(request);
        PageResult<ProcessInstanceResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取流程实例详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:instance:query')")
    public Result<ProcessInstanceResponse> getInfo(@PathVariable String id) {
        return Result.success(wfProcessInstanceService.getInstanceById(id));
    }

    @Operation(summary = "发起流程")
    @PostMapping("/start")
    @PreAuthorize("hasAuthority('workflow:instance:start')")
    @OperationLog(title = "流程实例管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> startProcess(@Valid @RequestBody ProcessStartRequest request) {
        wfProcessInstanceService.startProcess(request);
        return Result.success();
    }

    @Operation(summary = "取消流程实例")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:instance:cancel')")
    @OperationLog(title = "流程实例管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> cancel(@PathVariable String id) {
        wfProcessInstanceService.cancelProcess(id);
        return Result.success();
    }

    @Operation(summary = "获取流程实例高亮流程图")
    @GetMapping("/{id}/diagram")
    @PreAuthorize("hasAuthority('workflow:instance:query')")
    public ResponseEntity<byte[]> getDiagram(@PathVariable String id) {
        try (var inputStream = wfProcessInstanceService.getInstanceDiagram(id)) {
            byte[] diagramBytes = inputStream.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                    .body(diagramBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "获取流程实例审批意见")
    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('workflow:instance:query')")
    public Result<List<ApprovalCommentResponse>> getComments(@PathVariable String id) {
        return Result.success(wfProcessInstanceService.getApprovalComments(id));
    }

    @Operation(summary = "获取流程实例变量")
    @GetMapping("/{id}/variables")
    @PreAuthorize("hasAuthority('workflow:instance:query')")
    public Result<Map<String, Object>> getVariables(@PathVariable String id) {
        return Result.success(wfProcessInstanceService.getProcessVariables(id));
    }

    @Operation(summary = "获取审批详情（时间线）")
    @GetMapping("/{id}/approval-detail")
    @PreAuthorize("hasAuthority('workflow:instance:query')")
    public Result<ApprovalDetailResponse> getApprovalDetail(@PathVariable String id) {
        return Result.success(wfProcessInstanceService.getApprovalDetail(id));
    }
}
