package com.forge.modules.ai.agent.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.kernel.AgentGraphRunner;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentJob;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentTeam;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiAgentJobMapper;
import com.forge.modules.ai.service.AiAgentTeamService;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.job.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Agent 定时任务执行器：sys_job.invokeTarget = agentJobTask
 * job_params = {"agentJobId": <ai_agent_job.id>}；Quartz 线程显式绑定租户上下文后执行编排。
 */
@Slf4j
@Component("agentJobTask")
@RequiredArgsConstructor
public class AgentJobTask implements JobHandler {

    private final AiAgentJobMapper agentJobMapper;
    private final AiAgentConfigMapper agentConfigMapper;
    private final AiAgentTeamService teamService;
    private final AgentGraphRunner graphRunner;

    @Override
    public void execute(Map<String, Object> params) {
        if (params == null || params.get("agentJobId") == null) {
            throw new BusinessException("Agent 任务参数缺失: agentJobId");
        }
        long agentJobId = Long.parseLong(String.valueOf(params.get("agentJobId")));
        run(agentJobId, "SCHEDULED");
    }

    /**
     * 手动执行（trigger=MANUAL）与调度执行共用路径
     */
    public AiAgentRun run(long agentJobId, String triggerType) {
        AiAgentJob job = agentJobMapper.selectById(agentJobId);
        if (job == null) {
            throw new BusinessException("Agent 任务不存在: " + agentJobId);
        }
        Long tenantId = job.getTenantId();
        Long previousTenantId = TenantContextHolder.getTenantId();
        try {
            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
            }
            AiAgentRun run = doRun(job, triggerType);
            job.setLastRunId(run.getId());
            agentJobMapper.updateById(job);
            return run;
        } finally {
            if (tenantId != null) {
                if (previousTenantId != null) {
                    TenantContextHolder.setTenantId(previousTenantId);
                } else {
                    TenantContextHolder.clear();
                }
            }
        }
    }

    private AiAgentRun doRun(AiAgentJob job, String triggerType) {
        String input = renderTemplate(job.getInputTemplate());
        if (job.getTeamCode() != null && !job.getTeamCode().isBlank()) {
            AiAgentTeam team = teamService.getEnabledByCode(job.getTeamCode());
            teamService.loadEnabledMembers(team.getId());
            return graphRunner.runTeamStream(team, null, 0L, triggerType, input, null, event -> { });
        }
        AiAgentConfig config = agentConfigMapper.selectOne(new LambdaQueryWrapper<AiAgentConfig>()
                .eq(AiAgentConfig::getAgentCode, job.getAgentCode())
                .eq(AiAgentConfig::getStatus, 1)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException("Agent 不存在或未启用: " + job.getAgentCode());
        }
        return graphRunner.runStream(config, null, 0L, triggerType, input, null, event -> { });
    }

    /**
     * 占位符：{date} 当日、{yesterday} 昨日、{datetime} 当前时刻
     */
    String renderTemplate(String template) {
        if (template == null || template.isBlank()) {
            return "请执行例行任务。";
        }
        LocalDate today = LocalDate.now();
        return template
                .replace("{date}", today.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .replace("{yesterday}", today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
                .replace("{datetime}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }
}
