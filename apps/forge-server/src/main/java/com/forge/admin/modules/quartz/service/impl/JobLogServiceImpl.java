package com.forge.admin.modules.quartz.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.modules.quartz.entity.SysJobLog;
import com.forge.admin.modules.quartz.mapper.SysJobLogMapper;
import com.forge.admin.modules.quartz.service.JobLogService;
import com.forge.admin.modules.system.entity.SysJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 任务执行日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogServiceImpl extends ServiceImpl<SysJobLogMapper, SysJobLog> implements JobLogService {

    private final SysJobLogMapper sysJobLogMapper;

    @Override
    public Page<SysJobLog> pageJobLogs(Long jobId, String jobName, Integer status, int pageNum, int pageSize) {
        Page<SysJobLog> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(jobId != null, SysJobLog::getJobId, jobId)
                .like(StrUtil.isNotBlank(jobName), SysJobLog::getJobName, jobName)
                .eq(status != null, SysJobLog::getStatus, status)
                .orderByDesc(SysJobLog::getCreateTime);

        return sysJobLogMapper.selectPage(page, wrapper);
    }

    @Override
    @Async
    public void recordJobLog(SysJob job, long startTime, long duration, boolean success, String errorMessage) {
        try {
            SysJobLog jobLog = new SysJobLog();
            jobLog.setJobId(job.getId());
            jobLog.setJobName(job.getJobName());
            jobLog.setJobGroup(job.getJobGroup());
            jobLog.setInvokeTarget(job.getInvokeTarget());
            jobLog.setStatus(success ? 1 : 0);
            jobLog.setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault()));
            jobLog.setEndTime(LocalDateTime.now());
            jobLog.setDuration(duration);

            if (success) {
                jobLog.setJobMessage("任务执行成功");
            } else {
                jobLog.setJobMessage("任务执行失败");
                // 限制异常信息长度
                if (errorMessage != null && errorMessage.length() > 2000) {
                    errorMessage = errorMessage.substring(0, 2000);
                }
                jobLog.setExceptionInfo(errorMessage);
            }

            sysJobLogMapper.insert(jobLog);

            log.debug("记录任务执行日志: {} - {}", job.getJobName(), success ? "成功" : "失败");

        } catch (Exception e) {
            log.error("记录任务执行日志失败", e);
        }
    }

    @Override
    public void clearJobLogs(Long jobId) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(jobId != null, SysJobLog::getJobId, jobId);
        sysJobLogMapper.delete(wrapper);
    }
}
