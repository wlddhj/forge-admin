package com.forge.modules.system.service;

import com.forge.modules.system.dto.DashboardStats;

public interface DashboardService {

    /**
     * 首页仪表盘统计数据（用户/角色/菜单/部门/岗位/字典/配置/日志总数）
     */
    DashboardStats getStats();
}
