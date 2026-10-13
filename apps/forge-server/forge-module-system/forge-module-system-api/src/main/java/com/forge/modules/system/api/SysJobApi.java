package com.forge.modules.system.api;

import com.forge.modules.system.dto.job.JobRequest;

/**
 * 定时任务跨模块门面：供其他业务模块（如 AI Agent 调度）联动 sys_job，
 * 实现位于 system-biz，运行时注入。
 */
public interface SysJobApi {

    /**
     * 新增任务并按状态注册调度；返回 sys_job 主键
     */
    Long addJob(JobRequest request);

    /**
     * 更新任务（含 cron 变更重排）
     */
    void updateJob(JobRequest request);

    /**
     * 删除任务（含移除调度）
     */
    void deleteJobs(Long[] ids);

    /**
     * 启用/暂停任务
     */
    void changeStatus(Long id, Integer status);
}
