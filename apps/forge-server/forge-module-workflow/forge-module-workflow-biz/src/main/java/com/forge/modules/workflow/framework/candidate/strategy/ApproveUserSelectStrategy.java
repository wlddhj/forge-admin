package com.forge.modules.workflow.framework.candidate.strategy;

import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.framework.candidate.CandidateStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批人自选候选人策略 - FlowLong 版本
 * 上一个审批人在审批时选择下一个节点的审批人，存储在流程变量中
 * 变量名：NEXT_TASK_DEF_KEY_candidateUsers
 *
 * @author forge-admin
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
    public Set<Long> calculateUsers(String param, TaskContext taskContext) {
        try {
            String taskKey = taskContext.getTaskKey();
            String varName = "NEXT_" + taskKey + "_candidateUsers";

            Map<String, Object> variables = taskContext.getVariables();
            if (variables != null && variables.containsKey(varName)) {
                Object candidateUsers = variables.get(varName);
                if (candidateUsers instanceof String && !((String) candidateUsers).isEmpty()) {
                    Set<Long> userIds = Arrays.stream(((String) candidateUsers).split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Long::parseLong)
                            .collect(Collectors.toSet());
                    log.debug("审批人自选候选人: taskKey={}, userIds={}", taskKey, userIds);
                    return userIds;
                }
            }
        } catch (Exception e) {
            log.warn("获取审批人自选候选人失败: {}", e.getMessage());
        }
        return Collections.emptySet();
    }
}