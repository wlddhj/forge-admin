package com.forge.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.utils.ExcelUtils;
import com.forge.modules.system.dto.log.LoginLogExport;
import com.forge.modules.system.dto.log.LoginLogQueryRequest;
import com.forge.modules.system.dto.log.LoginLogResponse;
import com.forge.modules.system.service.SysLoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 登录日志控制器
 */
@Tag(name = "登录日志管理")
@RestController
@RequestMapping("/system/login-log")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService sysLoginLogService;

    @Operation(summary = "分页查询登录日志")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:login-log:list')")
    public Result<PageResult<LoginLogResponse>> list(LoginLogQueryRequest request) {
        Page<LoginLogResponse> page = sysLoginLogService.pageLogs(request);
        PageResult<LoginLogResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "清空登录日志")
    @DeleteMapping("/clear")
    @PreAuthorize("hasAuthority('system:login-log:clear')")
    public Result<Void> clear() {
        sysLoginLogService.clearLogs();
        return Result.success();
    }

    @Operation(summary = "导出登录日志")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:login-log:export')")
    @OperationLog(title = "登录日志", businessType = OperationLog.BusinessType.EXPORT)
    public void export(LoginLogQueryRequest request, HttpServletResponse response) {
        List<LoginLogExport> list = sysLoginLogService.getExportList(request);
        ExcelUtils.export(response, "登录日志", "登录日志", LoginLogExport.class, list);
    }
}
