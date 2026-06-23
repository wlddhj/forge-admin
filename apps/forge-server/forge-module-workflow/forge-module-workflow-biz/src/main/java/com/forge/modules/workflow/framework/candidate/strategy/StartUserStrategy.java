package com.forge.modules.workflow.framework.candidate.strategy;

import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.framework.candidate.CandidateStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 发起人自己候选人策略 - FlowLong 版本
 * 将流程发起人作为审批人
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class StartUserStrategy implements BpmTaskCandidateStrategy {

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.START_USER.getCode();
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
    public Set<Long> calculateUsers(String param, TaskContext taskContext) {
        try {
            Long startUserId = taskContext.getStartUserId();
            if (startUserId != null) {
                log.debug("发起人自己候选人: startUserId={}", startUserId);
                return Set.of(startUserId);
            }
        } catch (Exception e) {
            log.warn("获取流程发起人失败: {}", e.getMessage());
        }
        return Collections.emptySet();
    }
}