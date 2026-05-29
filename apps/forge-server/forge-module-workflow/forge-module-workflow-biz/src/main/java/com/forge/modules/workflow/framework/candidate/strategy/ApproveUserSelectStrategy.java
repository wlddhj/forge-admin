package com.forge.modules.workflow.framework.candidate.strategy;

import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.framework.candidate.CandidateStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批人自选候选人策略
 * 上一个审批人在审批时选择下一个节点的审批人，存储在流程变量中
 * 变量名：NEXT_TASK_DEF_KEY_candidateUsers
 */
@Slf4j
@Component
public class ApproveUserSelectStrategy implements BpmTaskCandidateStrategy {

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.APPROVE_USER_SELECT.getCode();
    }

    @Override
    public String getDescription() {
        return "审批人自选";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        return Collections.emptySet();
    }

    @Override
    public Set<Long> calculateUsers(String param, DelegateTask delegateTask) {
        try {
            String taskDefKey = delegateTask.getTaskDefinitionKey();
            String varName = "NEXT_" + taskDefKey + "_candidateUsers";
            Object candidateUsers = delegateTask.getVariable(varName);
            if (candidateUsers instanceof String && !((String) candidateUsers).isEmpty()) {
                return Arrays.stream(((String) candidateUsers).split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::parseLong)
                        .collect(Collectors.toSet());
            }
        } catch (Exception e) {
            log.warn("获取审批人自选候选人失败: {}", e.getMessage());
        }
        return Collections.emptySet();
    }
}
