package com.forge.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.modules.system.dto.log.LoginLogExport;
import com.forge.admin.modules.system.dto.log.LoginLogQueryRequest;
import com.forge.admin.modules.system.dto.log.LoginLogResponse;
import com.forge.admin.modules.system.entity.SysLoginLog;
import com.forge.admin.modules.system.mapper.SysLoginLogMapper;
import com.forge.admin.modules.system.service.SysLoginLogService;
import com.forge.admin.common.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 登录日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog> implements SysLoginLogService {

    private final SysLoginLogMapper sysLoginLogMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<LoginLogResponse> pageLogs(LoginLogQueryRequest request) {
        Page<SysLoginLog> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getUsername())) {
            wrapper.like(SysLoginLog::getUsername, request.getUsername());
        }
        if (StringUtils.hasText(request.getLoginIp())) {
            wrapper.like(SysLoginLog::getLoginIp, request.getLoginIp());
        }
        if (request.getStatus() != null) {
            wrapper.eq(SysLoginLog::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(SysLoginLog::getLoginTime, LocalDateTime.parse(request.getStartTime(), DATE_TIME_FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(SysLoginLog::getLoginTime, LocalDateTime.parse(request.getEndTime(), DATE_TIME_FORMATTER));
        }
        wrapper.orderByDesc(SysLoginLog::getLoginTime);

        Page<SysLoginLog> result = sysLoginLogMapper.selectPage(page, wrapper);

        Page<LoginLogResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::convertToResponse).toList());
        return responsePage;
    }

    @Override
    @Async
    public void recordLoginLog(String username, Integer status, String msg, HttpServletRequest request) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUsername(username);
            loginLog.setStatus(status);
            loginLog.setMsg(msg);
            loginLog.setLoginTime(LocalDateTime.now());

            if (request != null) {
                // 获取客户端IP
                String ip = IpUtils.getClientIp(request);
                loginLog.setLoginIp(ip);

                // 获取登录地点（简化处理，实际可对接IP地址库）
                loginLog.setLoginLocation(IpUtils.getLocationByIp(ip));

                // 获取浏览器信息
                String userAgent = request.getHeader("User-Agent");
                loginLog.setBrowser(IpUtils.getBrowser(userAgent));
                loginLog.setOs(IpUtils.getOs(userAgent));
            }

            save(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败", e);
        }
    }

    @Override
    public void clearLogs() {
        sysLoginLogMapper.delete(null);
    }

    @Override
    public List<LoginLogExport> getExportList(LoginLogQueryRequest request) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getUsername())) {
            wrapper.like(SysLoginLog::getUsername, request.getUsername());
        }
        if (StringUtils.hasText(request.getLoginIp())) {
            wrapper.like(SysLoginLog::getLoginIp, request.getLoginIp());
        }
        if (request.getStatus() != null) {
            wrapper.eq(SysLoginLog::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(SysLoginLog::getLoginTime, LocalDateTime.parse(request.getStartTime(), DATE_TIME_FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(SysLoginLog::getLoginTime, LocalDateTime.parse(request.getEndTime(), DATE_TIME_FORMATTER));
        }
        wrapper.orderByDesc(SysLoginLog::getLoginTime);

        return list(wrapper).stream().map(log -> {
            LoginLogExport export = new LoginLogExport();
            export.setUsername(log.getUsername());
            export.setLoginIp(log.getLoginIp());
            export.setLoginLocation(log.getLoginLocation());
            export.setBrowser(log.getBrowser());
            export.setOs(log.getOs());
            export.setStatus(log.getStatus() == 1 ? "成功" : "失败");
            export.setMsg(log.getMsg());
            export.setLoginTime(log.getLoginTime() != null ? log.getLoginTime().format(DATE_TIME_FORMATTER) : "");
            return export;
        }).toList();
    }

    private LoginLogResponse convertToResponse(SysLoginLog log) {
        LoginLogResponse response = new LoginLogResponse();
        BeanUtils.copyProperties(log, response);
        return response;
    }
}
