package com.forge.admin.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.system.dto.DashboardStats;
import com.forge.admin.modules.system.entity.*;
import com.forge.admin.modules.system.mapper.*;
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

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysPositionMapper sysPositionMapper;
    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysOperationLogMapper sysOperationLogMapper;

    @Operation(summary = "获取统计数据")
    @GetMapping("/stats")
    public Result<DashboardStats> getStats() {
        DashboardStats stats = new DashboardStats();

        // 用户总数（未删除）
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getDeleted, 0);
        stats.setUserCount(sysUserMapper.selectCount(userWrapper));

        // 角色总数
        stats.setRoleCount(sysRoleMapper.selectCount(null));

        // 菜单总数
        stats.setMenuCount(sysMenuMapper.selectCount(null));

        // 部门总数
        stats.setDeptCount(sysDeptMapper.selectCount(null));

        // 岗位总数
        stats.setPositionCount(sysPositionMapper.selectCount(null));

        // 字典类型总数
        stats.setDictCount(sysDictTypeMapper.selectCount(null));

        // 配置总数
        stats.setConfigCount(sysConfigMapper.selectCount(null));

        // 操作日志总数
        stats.setLogCount(sysOperationLogMapper.selectCount(null));

        return Result.success(stats);
    }
}
