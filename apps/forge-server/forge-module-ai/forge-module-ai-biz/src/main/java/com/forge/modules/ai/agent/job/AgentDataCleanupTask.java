package com.forge.modules.ai.agent.job;

import com.forge.modules.ai.agent.lifecycle.AgentDataCleanupService;
import com.forge.modules.system.job.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 运行数据清理定时任务：sys_job.invokeTarget = agentDataCleanupTask
 * 按保留期分批物理清理 run/step/todo/checkpoint（跨租户，每日 03:00）
 */
@Slf4j
@Component("agentDataCleanupTask")
@RequiredArgsConstructor
public class AgentDataCleanupTask implements JobHandler {

    private final AgentDataCleanupService cleanupService;

    @Override
    public void execute(Map<String, Object> params) {
        cleanupService.cleanupAll();
    }
}
