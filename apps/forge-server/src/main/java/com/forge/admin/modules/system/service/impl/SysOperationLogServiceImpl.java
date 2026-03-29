package com.forge.admin.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.modules.system.dto.log.OperationLogQueryRequest;
import com.forge.admin.modules.system.dto.log.OperationLogResponse;
import com.forge.admin.modules.system.entity.SysOperationLog;
import com.forge.admin.modules.system.mapper.SysOperationLogMapper;
import com.forge.admin.modules.system.service.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志服务实现
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog> implements SysOperationLogService {

    private final SysOperationLogMapper sysOperationLogMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<OperationLogResponse> pageLogs(OperationLogQueryRequest request) {
        Page<SysOperationLog> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getTitle())) {
            wrapper.like(SysOperationLog::getTitle, request.getTitle());
        }
        if (StringUtils.hasText(request.getOperatorName())) {
            wrapper.like(SysOperationLog::getOperatorName, request.getOperatorName());
        }
        if (StringUtils.hasText(request.getBusinessType())) {
            wrapper.eq(SysOperationLog::getBusinessType, request.getBusinessType());
        }
        if (request.getStatus() != null) {
            wrapper.eq(SysOperationLog::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getStartTime())) {
            wrapper.ge(SysOperationLog::getOperateTime, LocalDateTime.parse(request.getStartTime(), DATE_TIME_FORMATTER));
        }
        if (StringUtils.hasText(request.getEndTime())) {
            wrapper.le(SysOperationLog::getOperateTime, LocalDateTime.parse(request.getEndTime(), DATE_TIME_FORMATTER));
        }
        wrapper.orderByDesc(SysOperationLog::getOperateTime);

        Page<SysOperationLog> result = sysOperationLogMapper.selectPage(page, wrapper);

        Page<OperationLogResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(this::convertToResponse).toList());
        return responsePage;
    }

    @Override
    public OperationLogResponse getLogDetail(Long id) {
        SysOperationLog log = sysOperationLogMapper.selectById(id);
        if (log == null) {
            throw new RuntimeException("操作日志不存在");
        }
        return convertToResponse(log);
    }

    @Override
    public void clearLogs() {
        sysOperationLogMapper.delete(null);
    }

    private OperationLogResponse convertToResponse(SysOperationLog log) {
        OperationLogResponse response = new OperationLogResponse();
        BeanUtils.copyProperties(log, response);
        return response;
    }
}
