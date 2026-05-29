package com.forge.modules.workflow.framework.candidate.strategy;

import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import com.forge.modules.workflow.framework.candidate.CandidateStrategyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * 表达式候选人策略
 * 解析 Flowable 表达式计算候选人
 * 表达式示例：${initiator}, ${deptLeader}, ${formField_userId}
 *
 * 暂时返回空集合，后续可集成 Flowable 表达式引擎
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class ExpressionCandidateStrategy implements BpmTaskCandidateStrategy {

    @Override
    public int getStrategy() {
        return CandidateStrategyEnum.EXPRESSION.getCode();
    }

    @Override
    public String getDescription() {
        return "表达式";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }
        // 表达式策略需要更多上下文信息（流程变量、表单数据等）
        // 暂时返回空集合，后续实现表达式解析逻辑
        log.warn("表达式候选人策略暂未实现完整功能: expression={}", param);
        return Collections.emptySet();
    }
}