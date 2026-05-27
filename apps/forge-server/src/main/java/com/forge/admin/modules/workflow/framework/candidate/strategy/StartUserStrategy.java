package com.forge.admin.modules.workflow.framework.candidate.strategy;

import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 发起人自己候选人策略
 * 将流程发起人作为审批人
 */
@Slf4j
@Component
public class StartUserStrategy implements BpmTaskCandidateStrategy {

    private RuntimeService runtimeService;

    @Autowired
    @Lazy
    public void setRuntimeService(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public int getStrategy() {
        return 36;
    }

    @Override
    public String getDescription() {
        return "发起人自己";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        return Collections.emptySet();
    }

    @Override
    public Set<Long> calculateUsers(String param, DelegateTask delegateTask) {
        try {
            String processInstanceId = delegateTask.getProcessInstanceId();
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (processInstance != null && processInstance.getStartUserId() != null) {
                return Set.of(Long.parseLong(processInstance.getStartUserId()));
            }
        } catch (Exception e) {
            log.warn("获取流程发起人失败: {}", e.getMessage());
        }
        return Collections.emptySet();
    }
}
