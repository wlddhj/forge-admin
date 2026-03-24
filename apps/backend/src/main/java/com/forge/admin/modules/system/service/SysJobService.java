package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.job.JobQueryRequest;
import com.forge.admin.modules.system.dto.job.JobRequest;
import com.forge.admin.modules.system.dto.job.JobResponse;
import com.forge.admin.modules.system.entity.SysJob;

/**
 * 定时任务服务接口
 */
public interface SysJobService extends IService<SysJob> {

    /**
     * 分页查询定时任务
     */
    Page<JobResponse> pageJobs(JobQueryRequest request);

    /**
     * 获取任务详情
     */
    JobResponse getJobDetail(Long id);

    /**
     * 新增任务
     */
    void addJob(JobRequest request);

    /**
     * 更新任务
     */
    void updateJob(JobRequest request);

    /**
     * 删除任务
     */
    void deleteJobs(Long[] ids);

    /**
     * 修改任务状态
     */
    void changeStatus(Long id, Integer status);

    /**
     * 立即执行一次
     */
    void runOnce(Long id);
}
