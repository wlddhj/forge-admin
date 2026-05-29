package com.forge.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.workflow.dto.definition.ProcessDefinitionQueryRequest;
import com.forge.modules.workflow.dto.definition.ProcessDefinitionResponse;
import com.forge.modules.workflow.dto.definition.ProcessDeployRequest;
import com.forge.modules.workflow.dto.definition.UserTaskNodeResponse;
import com.forge.modules.workflow.service.WfProcessDefinitionService;
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

/**
 * 流程定义管理控制器
 *
 * @author forge-admin
 */
@Tag(name = "流程定义管理")
@RestController
@RequestMapping("/workflow/process-definition")
@RequiredArgsConstructor
public class WfProcessDefinitionController {

    private final WfProcessDefinitionService wfProcessDefinitionService;

    @Operation(summary = "分页查询流程定义")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:process:list')")
    public Result<PageResult<ProcessDefinitionResponse>> list(ProcessDefinitionQueryRequest request) {
        Page<ProcessDefinitionResponse> page = wfProcessDefinitionService.pageDefinition(request);
        PageResult<ProcessDefinitionResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取流程定义详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow:process:query')")
    public Result<ProcessDefinitionResponse> getInfo(@PathVariable String id) {
        return Result.success(wfProcessDefinitionService.getDefinitionById(id));
    }

    @Operation(summary = "部署流程定义")
    @PostMapping("/deploy")
    @PreAuthorize("hasAuthority('workflow:process:deploy')")
    @OperationLog(title = "流程定义管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> deploy(@Valid @RequestBody ProcessDeployRequest request) {
        wfProcessDefinitionService.deploy(request);
        return Result.success();
    }

    @Operation(summary = "挂起流程定义")
    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('workflow:process:edit')")
    @OperationLog(title = "流程定义管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> suspend(@PathVariable String id) {
        wfProcessDefinitionService.suspendDefinition(id);
        return Result.success();
    }

    @Operation(summary = "激活流程定义")
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('workflow:process:edit')")
    @OperationLog(title = "流程定义管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> activate(@PathVariable String id) {
        wfProcessDefinitionService.activateDefinition(id);
        return Result.success();
    }

    @Operation(summary = "删除流程部署")
    @DeleteMapping("/{deploymentId}")
    @PreAuthorize("hasAuthority('workflow:process:delete')")
    @OperationLog(title = "流程定义管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable String deploymentId) {
        wfProcessDefinitionService.deleteDeployment(deploymentId);
        return Result.success();
    }

    @Operation(summary = "获取BPMN XML")
    @GetMapping("/{id}/xml")
    @PreAuthorize("hasAuthority('workflow:process:query')")
    public Result<String> getBpmnXml(@PathVariable String id) {
        return Result.success(wfProcessDefinitionService.getBpmnXml(id));
    }

    @Operation(summary = "获取需要自选审批人的用户任务节点")
    @GetMapping("/{id}/user-tasks")
    public Result<List<UserTaskNodeResponse>> getUserTaskNodes(@PathVariable String id) {
        return Result.success(wfProcessDefinitionService.getStartUserSelectTasks(id));
    }

    @Operation(summary = "获取流程图")
    @GetMapping("/{id}/diagram")
    @PreAuthorize("hasAuthority('workflow:process:query')")
    public ResponseEntity<byte[]> getDiagram(@PathVariable String id) {
        try (var inputStream = wfProcessDefinitionService.getDiagram(id)) {
            byte[] diagramBytes = inputStream.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                    .body(diagramBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
