package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.log.LoginLogQueryRequest;
import com.forge.admin.modules.system.dto.log.LoginLogResponse;
import com.forge.admin.modules.system.entity.SysLoginLog;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 登录日志服务接口
 */
public interface SysLoginLogService extends IService<SysLoginLog> {

    /**
     * 分页查询登录日志
     */
    Page<LoginLogResponse> pageLogs(LoginLogQueryRequest request);

    /**
     * 记录登录日志
     * @param username 用户名
     * @param status 状态(0:失败 1:成功)
     * @param msg 消息
     * @param request HTTP请求
     */
    void recordLoginLog(String username, Integer status, String msg, HttpServletRequest request);

    /**
     * 清空登录日志
     */
    void clearLogs();
}
