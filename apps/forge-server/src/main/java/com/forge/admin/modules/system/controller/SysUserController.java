package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.PageResult;
import com.forge.admin.common.response.Result;
import com.forge.admin.common.utils.ExcelUtils;
import com.forge.admin.modules.system.dto.user.*;
import com.forge.admin.modules.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 用户控制器
 *
 * @author standadmin
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:user:list')")
    public Result<PageResult<UserResponse>> list(UserQueryRequest request) {
        Page<UserResponse> page = sysUserService.pageUsers(request);
        PageResult<UserResponse> result = PageResult.of(
                page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()
        );
        return Result.success(result);
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<UserResponse> getInfo(@PathVariable Long id) {
        return Result.success(sysUserService.getUserDetail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    @OperationLog(title = "用户管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Void> add(@Valid @RequestBody UserRequest request) {
        sysUserService.addUser(request);
        return Result.success();
    }

    @Operation(summary = "更新用户")
    @PutMapping
    @PreAuthorize("hasAuthority('system:user:edit')")
    @OperationLog(title = "用户管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody UserRequest request) {
        sysUserService.updateUser(request);
        return Result.success();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @OperationLog(title = "用户管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable List<Long> ids) {
        sysUserService.deleteUsers(ids);
        return Result.success();
    }

    @Operation(summary = "导出用户")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:user:export')")
    public void export(UserQueryRequest request, HttpServletResponse response) {
        List<UserExport> list = sysUserService.getExportList(request);
        ExcelUtils.export(response, "用户列表", "用户数据", UserExport.class, list);
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('system:user:import')")
    public void importTemplate(HttpServletResponse response) {
        List<UserImportDTO> list = Arrays.asList(
                new UserImportDTO() {{
                    setUsername("zhangsan");
                    setNickname("张三");
                    setPhone("13800138000");
                    setEmail("zhangsan@example.com");
                    setDeptId(1L);
                    setStatus(1);
                }},
                new UserImportDTO() {{
                    setUsername("lisi");
                    setNickname("李四");
                    setPhone("13900139000");
                    setEmail("lisi@example.com");
                    setDeptId(2L);
                    setStatus(1);
                }}
        );
        ExcelUtils.export(response, "用户导入模板", "用户列表", UserImportDTO.class, list);
    }

    @Operation(summary = "导入用户")
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('system:user:import')")
    @OperationLog(title = "用户管理", businessType = OperationLog.BusinessType.IMPORT)
    public Result<UserImportResultDTO> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateSupport", required = false, defaultValue = "false") Boolean updateSupport
    ) {
        List<UserImportDTO> list = ExcelUtils.read(file, UserImportDTO.class);
        return Result.success(sysUserService.importUsers(list, updateSupport));
    }

    @Operation(summary = "更新用户状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @OperationLog(title = "用户管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysUserService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:resetPwd')")
    @OperationLog(title = "用户管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> resetPassword(@PathVariable Long id) {
        sysUserService.resetPassword(id);
        return Result.success();
    }

    // ==================== 个人中心 ====================

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody com.forge.admin.modules.system.dto.user.UserProfileRequest request) {
        sysUserService.updateProfile(request);
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    @OperationLog(title = "个人中心", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updatePassword(@Valid @RequestBody com.forge.admin.modules.system.dto.user.UserPasswordRequest request) {
        sysUserService.updatePassword(request);
        return Result.success();
    }

    @Operation(summary = "更新头像")
    @PutMapping("/avatar")
    @OperationLog(title = "个人中心", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateAvatar(@Valid @RequestBody com.forge.admin.modules.system.dto.user.UserAvatarRequest request) {
        sysUserService.updateAvatar(request);
        return Result.success();
    }
}
