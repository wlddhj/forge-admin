package com.forge.modules.system.api;

import com.forge.modules.system.dto.job.JobRequest;
import com.forge.modules.system.service.SysJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 定时任务门面实现：委托 SysJobService
 */
@Service
@RequiredArgsConstructor
public class SysJobApiImpl implements SysJobApi {

    private final SysJobService sysJobService;

    @Override
    public Long addJob(JobRequest request) {
        return sysJobService.addJob(request);
    }

    @Override
    public void updateJob(JobRequest request) {
        sysJobService.updateJob(request);
    }

    @Override
    public void deleteJobs(Long[] ids) {
        sysJobService.deleteJobs(ids);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        sysJobService.changeStatus(id, status);
    }
}
