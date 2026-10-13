package com.forge.modules.ai.agent.job;

import com.forge.modules.ai.entity.AiAgentJob;
import com.forge.modules.ai.entity.AiAgentRun;

import java.util.Map;

/**
 * AI Agent 定时任务服务：ai_agent_job CRUD 与 sys_job 调度联动
 */
public interface AiAgentJobService {

    Map<String, Object> pageJobs(String jobName, String agentCode, Integer status, int pageNum, int pageSize);

    void addJob(AiAgentJob job);

    void updateJob(Long id, AiAgentJob job);

    void changeStatus(Long id, Integer status);

    void deleteJob(Long id);

    /**
     * 立即执行一次（同步返回 run 结果）
     */
    AiAgentRun runOnce(Long id);
}
