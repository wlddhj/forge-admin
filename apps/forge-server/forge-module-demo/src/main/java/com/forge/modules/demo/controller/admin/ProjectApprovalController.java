package com.forge.modules.demo.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.modules.demo.dto.ProjectResponse;
import com.forge.modules.demo.dto.ProjectSubmitRequest;
import com.forge.modules.demo.service.ProjectApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目立项管理（业务模块接入工作流示例）
 *
 * @author forge-admin
 */
@Tag(name = "项目立项管理")
@RestController
@RequestMapping("/demo/project")
@RequiredArgsConstructor
public class ProjectApprovalController {

    private final ProjectApprovalService projectService;

    @Operation(summary = "分页查询立项记录")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('demo:project:list')")
    public Result<PageResult<ProjectResponse>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @RequestParam(required = false) String projectName,
                                                    @RequestParam(required = false) String approveStatus) {
        Page<ProjectResponse> page = projectService.pageProjects(pageNum, pageSize, projectName, approveStatus);
        PageResult<ProjectResponse> result = PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
        return Result.success(result);
    }

    @Operation(summary = "立项详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('demo:project:query')")
    public Result<ProjectResponse> getInfo(@PathVariable Long id) {
        return Result.success(projectService.getProject(id));
    }

    @Operation(summary = "提交立项并发起审批流程")
    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('demo:project:add')")
    public Result<Long> submit(@Valid @RequestBody ProjectSubmitRequest request) {
        return Result.success(projectService.submitProject(request));
    }

    @Operation(summary = "发起人撤销（流程实例撤销，状态由事件回写）")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('demo:project:edit')")
    public Result<Void> cancel(@PathVariable Long id) {
        projectService.cancelProject(id);
        return Result.success();
    }
}
