package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.log.LoginLogExport;
import com.forge.admin.modules.system.dto.log.LoginLogQueryRequest;
import com.forge.admin.modules.system.dto.log.LoginLogResponse;
import com.forge.admin.modules.system.entity.SysLoginLog;
import java.util.List;

/**
 * 登录日志服务接口
 */
public interface SysLoginLogService extends IService<SysLoginLog> {

    /**
     * 分页查询登录日志
     */
    Page<LoginLogResponse> pageLogs(LoginLogQueryRequest request);

    /**
     * 记录登录日志（异步）
     * @param username 用户名
     * @param status 状态(0:失败 1:成功)
     * @param msg 消息
     * @param loginIp 客户端IP
     * @param userAgent 浏览器User-Agent
     */
    void recordLoginLog(String username, Integer status, String msg, String loginIp, String userAgent);

    /**
     * 清空登录日志
     */
    void clearLogs();

    /**
     * 获取导出列表
     */
    List<LoginLogExport> getExportList(LoginLogQueryRequest request);
}
