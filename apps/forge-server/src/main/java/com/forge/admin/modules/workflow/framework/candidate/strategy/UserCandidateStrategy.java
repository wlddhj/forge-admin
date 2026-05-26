package com.forge.admin.modules.workflow.framework.candidate.strategy;

import com.forge.admin.modules.workflow.framework.candidate.BpmTaskCandidateStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 指定用户候选人策略
 * 直接使用传入的用户ID作为候选人
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class UserCandidateStrategy implements BpmTaskCandidateStrategy {

    @Override
    public int getStrategy() {
        return 30;
    }

    @Override
    public String getDescription() {
        return "指定用户";
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        if (param == null || param.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            Set<Long> userIds = Arrays.stream(param.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
            log.debug("指定用户候选人计算完成: userIds={}", userIds);
            return userIds;
        } catch (NumberFormatException e) {
            log.warn("指定用户候选人参数解析失败: param={}", param, e);
            return Collections.emptySet();
        }
    }
}