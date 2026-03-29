package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.quartz.service.ScheduleService;
import com.forge.admin.modules.system.dto.job.JobQueryRequest;
import com.forge.admin.modules.system.dto.job.JobRequest;
import com.forge.admin.modules.system.dto.job.JobResponse;
import com.forge.admin.modules.system.entity.SysJob;
import com.forge.admin.modules.system.mapper.SysJobMapper;
import com.forge.admin.modules.system.service.SysJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 定时任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobServiceImpl extends ServiceImpl<SysJobMapper, SysJob> implements SysJobService {

    private final SysJobMapper sysJobMapper;
    private final ScheduleService scheduleService;

    @Override
    public Page<JobResponse> pageJobs(JobQueryRequest request) {
        Page<SysJob> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getJobName()), SysJob::getJobName, request.getJobName())
                .eq(StrUtil.isNotBlank(request.getJobGroup()), SysJob::getJobGroup, request.getJobGroup())
                .eq(request.getStatus() != null, SysJob::getStatus, request.getStatus())
                .orderByDesc(SysJob::getCreateTime);

        Page<SysJob> jobPage = sysJobMapper.selectPage(page, wrapper);

        Page<JobResponse> responsePage = new Page<>();
        responsePage.setCurrent(jobPage.getCurrent());
        responsePage.setSize(jobPage.getSize());
        responsePage.setTotal(jobPage.getTotal());
        responsePage.setRecords(jobPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public JobResponse getJobDetail(Long id) {
        SysJob job = getById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return convertToResponse(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addJob(JobRequest request) {
        // 验证cron表达式
        if (!CronExpression.isValidExpression(request.getCronExpression())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "cron表达式格式不正确");
        }

        SysJob job = new SysJob();
        BeanUtil.copyProperties(request, job);
        save(job);

        // 如果状态是正常，则创建定时任务
        if (job.getStatus() == 1) {
            scheduleService.scheduleJob(job);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(JobRequest request) {
        SysJob job = getById(request.getId());
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        // 验证cron表达式
        if (!CronExpression.isValidExpression(request.getCronExpression())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "cron表达式格式不正确");
        }

        // 如果任务正在运行，先暂停
        if (job.getStatus() == 1) {
            scheduleService.pauseJob(job);
        }

        BeanUtil.copyProperties(request, job);
        updateById(job);

        // 如果状态是正常，重新创建定时任务
        if (job.getStatus() == 1) {
            scheduleService.scheduleJob(job);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteJobs(Long[] ids) {
        for (Long id : ids) {
            SysJob job = getById(id);
            if (job != null && job.getStatus() == 1) {
                scheduleService.deleteJob(job);
            }
        }
        removeByIds(Arrays.asList(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        SysJob job = getById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        if (status == 1) {
            // 恢复任务
            scheduleService.resumeJob(job);
        } else {
            // 暂停任务
            scheduleService.pauseJob(job);
        }

        job.setStatus(status);
        updateById(job);
    }

    @Override
    public void runOnce(Long id) {
        SysJob job = getById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        // 立即执行任务
        scheduleService.runOnce(job);
    }

    private JobResponse convertToResponse(SysJob job) {
        JobResponse response = new JobResponse();
        BeanUtil.copyProperties(job, response);

        // 计算下次执行时间
        try {
            CronExpression cronExpression = CronExpression.parse(job.getCronExpression());
            LocalDateTime nextTime = cronExpression.next(LocalDateTime.now());
            if (nextTime != null) {
                response.setNextValidTime(nextTime.toString());
            }
        } catch (Exception e) {
            log.warn("解析cron表达式失败: {}", job.getCronExpression());
        }

        return response;
    }
}
