package com.forge.modules.ai.agent.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.entity.AiAgentJob;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.mapper.AiAgentJobMapper;
import com.forge.modules.system.api.SysJobApi;
import com.forge.modules.system.dto.job.JobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 定时任务服务：ai_agent_job 为唯一事实源，CRUD 同事务联动 sys_job
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentJobServiceImpl implements AiAgentJobService {

    public static final String JOB_GROUP = "AI_AGENT";
    public static final String INVOKE_TARGET = "agentJobTask.execute";

    private final AiAgentJobMapper agentJobMapper;
    private final SysJobApi sysJobApi;
    private final AgentJobTask agentJobTask;

    @Override
    public Map<String, Object> pageJobs(String jobName, String agentCode, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<AiAgentJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(jobName != null && !jobName.isBlank(), AiAgentJob::getJobName, jobName);
        wrapper.eq(agentCode != null && !agentCode.isBlank(), AiAgentJob::getAgentCode, agentCode);
        wrapper.eq(status != null, AiAgentJob::getStatus, status);
        wrapper.orderByDesc(AiAgentJob::getId);
        Page<AiAgentJob> page = agentJobMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        return result;
    }

    @Override
    @Transactional
    public void addJob(AiAgentJob job) {
        validate(job);
        // 先插本地拿 id，sys_job.job_params 需引用 agentJobId；再回填 sys_job_id
        agentJobMapper.insert(job);
        JobRequest request = buildJobRequest(job);
        Long sysJobId = sysJobApi.addJob(request);
        job.setSysJobId(sysJobId);
        agentJobMapper.updateById(job);
    }

    @Override
    @Transactional
    public void updateJob(Long id, AiAgentJob job) {
        AiAgentJob existing = requireJob(id);
        if (job.getJobName() != null) {
            existing.setJobName(job.getJobName());
        }
        if (job.getAgentCode() != null) {
            existing.setAgentCode(job.getAgentCode());
        }
        if (job.getTeamCode() != null) {
            existing.setTeamCode(job.getTeamCode());
        }
        if (job.getInputTemplate() != null) {
            existing.setInputTemplate(job.getInputTemplate());
        }
        if (job.getCronExpression() != null) {
            existing.setCronExpression(job.getCronExpression());
        }
        validate(existing);
        if (existing.getSysJobId() != null) {
            JobRequest request = buildJobRequest(existing);
            request.setId(existing.getSysJobId());
            sysJobApi.updateJob(request);
        }
        agentJobMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        AiAgentJob existing = requireJob(id);
        existing.setStatus(status);
        if (existing.getSysJobId() != null) {
            sysJobApi.changeStatus(existing.getSysJobId(), status);
        }
        agentJobMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void deleteJob(Long id) {
        AiAgentJob existing = requireJob(id);
        if (existing.getSysJobId() != null) {
            sysJobApi.deleteJobs(new Long[]{existing.getSysJobId()});
        }
        agentJobMapper.deleteById(id);
    }

    @Override
    public AiAgentRun runOnce(Long id) {
        requireJob(id);
        return agentJobTask.run(id, "MANUAL");
    }

    private AiAgentJob requireJob(Long id) {
        AiAgentJob job = agentJobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException("Agent 任务不存在");
        }
        return job;
    }

    private void validate(AiAgentJob job) {
        if (job.getJobName() == null || job.getJobName().isBlank()) {
            throw new BusinessException("任务名称不能为空");
        }
        boolean hasAgent = job.getAgentCode() != null && !job.getAgentCode().isBlank();
        boolean hasTeam = job.getTeamCode() != null && !job.getTeamCode().isBlank();
        if (!hasAgent && !hasTeam) {
            throw new BusinessException("agentCode 与 teamCode 必须二选一");
        }
        if (hasAgent && hasTeam) {
            throw new BusinessException("agentCode 与 teamCode 只能二选一");
        }
        if (job.getCronExpression() == null || job.getCronExpression().isBlank()) {
            throw new BusinessException("cron 表达式不能为空");
        }
    }

    private JobRequest buildJobRequest(AiAgentJob job) {
        JobRequest request = new JobRequest();
        request.setJobName("agent:" + job.getJobName());
        request.setJobGroup(JOB_GROUP);
        request.setInvokeTarget(INVOKE_TARGET);
        request.setCronExpression(job.getCronExpression());
        request.setStatus(job.getStatus() == null ? 1 : job.getStatus());
        request.setConcurrent(0);
        request.setRetryCount(0);
        request.setRemark("由 Agent 任务管理维护，请勿在此编辑");
        request.setJobParams(Map.of("agentJobId", job.getId() == null ? 0 : job.getId()));
        return request;
    }
}
