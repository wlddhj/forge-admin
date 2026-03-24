package com.forge.admin.modules.quartz.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.quartz.entity.SysJobLog;
import com.forge.admin.modules.system.entity.SysJob;

/**
 * 任务执行日志服务接口
 */
public interface JobLogService extends IService<SysJobLog> {

    /**
     * 分页查询任务日志
     */
    Page<SysJobLog> pageJobLogs(Long jobId, String jobName, Integer status, int pageNum, int pageSize);

    /**
     * 记录任务执行日志
     */
    void recordJobLog(SysJob job, long startTime, long duration, boolean success, String errorMessage);

    /**
     * 清空任务日志
     */
    void clearJobLogs(Long jobId);
}
