package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.log.OperationLogExport;
import com.forge.admin.modules.system.dto.log.OperationLogQueryRequest;
import com.forge.admin.modules.system.dto.log.OperationLogResponse;
import com.forge.admin.modules.system.entity.SysOperationLog;

import java.util.List;

/**
 * 操作日志服务接口
 *
 * @author standadmin
 */
public interface SysOperationLogService extends IService<SysOperationLog> {

    /**
     * 分页查询操作日志
     */
    Page<OperationLogResponse> pageLogs(OperationLogQueryRequest request);

    /**
     * 获取操作日志详情
     */
    OperationLogResponse getLogDetail(Long id);

    /**
     * 清空日志
     */
    void clearLogs();

    /**
     * 获取导出列表
     */
    List<OperationLogExport> getExportList(OperationLogQueryRequest request);
}
