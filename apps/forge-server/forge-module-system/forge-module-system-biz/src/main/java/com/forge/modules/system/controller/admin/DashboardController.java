package com.forge.modules.system.controller.admin;

import com.forge.common.response.Result;
import com.forge.modules.system.dto.DashboardStats;
import com.forge.modules.system.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard控制器
 */
@Tag(name = "Dashboard")
@RestController
@RequestMapping("/system/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取统计数据")
    @GetMapping("/stats")
    public Result<DashboardStats> getStats() {
        return Result.success(dashboardService.getStats());
    }
}
