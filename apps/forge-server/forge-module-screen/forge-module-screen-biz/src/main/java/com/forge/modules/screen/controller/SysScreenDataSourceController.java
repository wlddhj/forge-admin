package com.forge.modules.screen.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.framework.web.annotation.RateLimiter;
import com.forge.modules.screen.dto.DataSourceExecuteRequest;
import com.forge.modules.screen.dto.DataSourceExecuteResponse;
import com.forge.modules.screen.dto.ScreenPageRequest;
import com.forge.modules.screen.entity.SysScreenDataSource;
import com.forge.modules.screen.service.SysScreenDataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大屏数据源 Controller。
 *
 * <p>提供数据源 CRUD 与执行（实际由熔断 + 缓存 + 执行器三层流水线处理）能力。
 *
 * @author standadmin
 */
@Tag(name = "大屏数据源")
@RestController
@RequestMapping("/screen/data-source")
@RequiredArgsConstructor
public class SysScreenDataSourceController {

    private final SysScreenDataSourceService dataSourceService;

    @Operation(summary = "分页查询数据源")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('screen:data-source:list')")
    public Result<PageResult<SysScreenDataSource>> list(ScreenPageRequest request) {
        Page<SysScreenDataSource> page = dataSourceService.page(request);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(),
                page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "查询数据源")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('screen:data-source:query')")
    public Result<SysScreenDataSource> get(@PathVariable Long id) {
        return Result.success(dataSourceService.getById(id));
    }

    @Operation(summary = "新增数据源")
    @PostMapping
    @PreAuthorize("hasAuthority('screen:data-source:add')")
    @OperationLog(title = "大屏数据源", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> add(@RequestBody SysScreenDataSource entity) {
        return Result.success(dataSourceService.create(entity));
    }

    @Operation(summary = "修改数据源")
    @PutMapping
    @PreAuthorize("hasAuthority('screen:data-source:edit')")
    @OperationLog(title = "大屏数据源", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@RequestBody SysScreenDataSource entity) {
        dataSourceService.update(entity);
        return Result.success();
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping
    @PreAuthorize("hasAuthority('screen:data-source:remove')")
    @OperationLog(title = "大屏数据源", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        dataSourceService.delete(ids);
        return Result.success();
    }

    @Operation(summary = "执行数据源查询")
    @PostMapping("/execute/{id}")
    @PreAuthorize("hasAuthority('screen:data-source:execute')")
    @OperationLog(title = "大屏数据源执行", businessType = OperationLog.BusinessType.OTHER)
    @RateLimiter(keyType = RateLimiter.KeyType.IP, keyPrefix = "screen_data_source_execute",
            time = 60, count = 60, message = "数据源执行过于频繁，请稍后再试")
    public Result<DataSourceExecuteResponse> execute(@PathVariable Long id,
                                                     @RequestBody DataSourceExecuteRequest request) {
        return Result.success(dataSourceService.execute(id, request));
    }
}
