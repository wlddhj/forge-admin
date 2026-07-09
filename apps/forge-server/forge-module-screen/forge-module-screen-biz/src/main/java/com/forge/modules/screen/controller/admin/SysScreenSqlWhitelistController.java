package com.forge.modules.screen.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.screen.dto.ScreenPageRequest;
import com.forge.modules.screen.entity.SysScreenSqlWhitelist;
import com.forge.modules.screen.service.SysScreenSqlWhitelistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大屏 SQL 白名单 Controller
 *
 * @author standadmin
 */
@Tag(name = "大屏 SQL 白名单")
@RestController
@RequestMapping("/screen/sql-whitelist")
@RequiredArgsConstructor
public class SysScreenSqlWhitelistController {

    private final SysScreenSqlWhitelistService sqlWhitelistService;

    @Operation(summary = "分页查询白名单")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('screen:sql-whitelist:list')")
    public Result<PageResult<SysScreenSqlWhitelist>> list(ScreenPageRequest request) {
        Page<SysScreenSqlWhitelist> page = sqlWhitelistService.lambdaQuery()
                .like(request.getName() != null && !request.getName().isBlank(),
                        SysScreenSqlWhitelist::getTableName, request.getName())
                .orderByDesc(SysScreenSqlWhitelist::getSchemaName)
                .orderByAsc(SysScreenSqlWhitelist::getTableName)
                .page(new Page<>(request.getPageNum(), request.getPageSize()));
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(),
                page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "查询白名单详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('screen:sql-whitelist:query')")
    public Result<SysScreenSqlWhitelist> get(@PathVariable Long id) {
        return Result.success(sqlWhitelistService.getById(id));
    }

    @Operation(summary = "新增白名单")
    @PostMapping
    @PreAuthorize("hasAuthority('screen:sql-whitelist:add')")
    @OperationLog(title = "大屏 SQL 白名单", businessType = OperationLog.BusinessType.INSERT)
    public Result<Long> add(@RequestBody SysScreenSqlWhitelist entity) {
        sqlWhitelistService.save(entity);
        return Result.success(entity.getId());
    }

    @Operation(summary = "修改白名单")
    @PutMapping
    @PreAuthorize("hasAuthority('screen:sql-whitelist:edit')")
    @OperationLog(title = "大屏 SQL 白名单", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> update(@RequestBody SysScreenSqlWhitelist entity) {
        sqlWhitelistService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除白名单")
    @DeleteMapping
    @PreAuthorize("hasAuthority('screen:sql-whitelist:remove')")
    @OperationLog(title = "大屏 SQL 白名单", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@RequestBody List<Long> ids) {
        sqlWhitelistService.removeByIds(ids);
        return Result.success();
    }
}
